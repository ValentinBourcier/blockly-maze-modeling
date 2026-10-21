#
# Exports one function, compute_models(), that runs the Java tool of the game on every
# session, and saves the metrics of its models (the programs) in one CSV file per
# session, in data/models/. read_models(), in read.R, reads these files back.
#
# Computing needs Java (17 or 21), Maven and the game repository (with blocky_game,
# blocky_momot and blocky_model). Reading only needs the CSV files, so the analysis
# can be reproduced without them.
#

# The columns kept from the Java tool: one row per model
model_columns <- c(
    "session", "index", "level", "type", "variant", "timestamp", "program",
    "matchesLoadedCandidate", "reachesGoal", "status", "power_kW_actions", "memory_kB_blocks", "note"
)

# Compute the metrics of the models of every session file of `data_folder`,
# and save them in `models_folder`, in a CSV file named after the session file.
# Sessions that already have their CSV file are skipped: delete a file to compute it again.
compute_models <- function(game_folder, data_folder = "data", models_folder = "data/models") {
    sessions <- list.files(data_folder, pattern = "\\.json$", full.names = TRUE)
    models_files <- file.path(models_folder, sub("\\.json$", ".csv", basename(sessions)))

    # The sessions left to compute
    to_compute <- !file.exists(models_files)
    if (!any(to_compute)) {
        message("The models of every session are already computed in ", models_folder)
        return(invisible(NULL))
    }

    if (!dir.exists(game_folder)) {
        stop("The game folder '", game_folder, "' does not exist")
    }
    # Full path, because the commands run from the game folder
    game_folder <- normalizePath(game_folder)

    classpath <- prepare_java_tool(game_folder)
    dir.create(models_folder, showWarnings = FALSE, recursive = TRUE)

    for (i in which(to_compute)) {
        message("Computing the models of ", basename(sessions[i]))
        models <- run_java_tool(game_folder, classpath, sessions[i])
        write_csv(models, models_files[i], na = "")
    }
}

#
# JAVA TOOL
#

# The libraries the game needs that are not on Maven Central.
# They come with the game, in its libs/ folder (same list as its Dockerfile).
game_libraries <- tribble(
    ~file,                                                         ~group,                   ~artifact,                            ~version,
    "at.ac.tuwien.big.momot.core_2.0.0.202604151118.jar",          "at.ac.tuwien.big.momot", "at.ac.tuwien.big.momot.core",        "2.0.0-SNAPSHOT",
    "at.ac.tuwien.big.moea_2.0.0.202604151118.jar",                "at.ac.tuwien.big.momot", "at.ac.tuwien.big.moea",              "2.0.0-SNAPSHOT",
    "org.eclipse.emf.henshin.model_1.8.0.202302121604.jar",        "org.eclipse.emf",        "org.eclipse.emf.henshin.model",      "1.8.0",
    "org.eclipse.emf.henshin.interpreter_1.8.0.202302121604.jar",  "org.eclipse.emf",        "org.eclipse.emf.henshin.interpreter", "1.8.0",
    "org.eclipse.ocl.ecore_3.23.0.v20260217-0639.jar",             "org.eclipse.ocl",        "org.eclipse.ocl.ecore",              "3.23.0",
    "org.eclipse.ocl.common_1.23.0.v20260217-0639.jar",            "org.eclipse.ocl",        "org.eclipse.ocl.common",             "1.23.0",
    "org.eclipse.ocl_3.23.0.v20260217-0639.jar",                   "org.eclipse.ocl",        "org.eclipse.ocl",                    "3.23.0",
    "org.eclipse.xtext.xbase.lib_2.42.0.v20260223-0608.jar",       "org.eclipse.xtext",      "org.eclipse.xtext.xbase.lib",        "2.42.0",
    "org.eclipse.core.runtime_3.34.200.v20251220-0953.jar",        "org.eclipse.core",       "runtime",                            "3.34.200",
    "org.eclipse.equinox.common_3.20.300.v20251111-0312.jar",      "org.eclipse.equinox",    "common",                             "3.20.300",
    "lpg.runtime.java_2.0.17.v201004271640.jar",                   "lpg.runtime",            "java",                               "2.0.17"
)

# Install the game libraries in the local Maven repository (~/.m2), so Maven can compile the game.
# Libraries already installed are skipped, so this only does something the first time.
install_game_libraries <- function(game_folder) {
    maven_repository <- file.path(path.expand("~"), ".m2", "repository")

    for (i in seq_len(nrow(game_libraries))) {
        lib <- game_libraries[i, ]
        installed_jar <- file.path(
            maven_repository, gsub(".", "/", lib$group, fixed = TRUE), lib$artifact, lib$version,
            paste0(lib$artifact, "-", lib$version, ".jar")
        )
        if (file.exists(installed_jar)) next

        message("Installing ", lib$file, " in the local Maven repository")
        run_command(game_folder, "mvn", c(
            "-q", "install:install-file", paste0("-Dfile=libs/", lib$file),
            paste0("-DgroupId=", lib$group), paste0("-DartifactId=", lib$artifact),
            paste0("-Dversion=", lib$version), "-Dpackaging=jar", "-DgeneratePom=true"
        ))
    }
}

# Compile the game and return the Java classpath to run its tool.
# The freshly compiled classes come first, so the current simulator of the game is used.
prepare_java_tool <- function(game_folder) {
    classpath_file <- tempfile(fileext = ".txt")
    on.exit(unlink(classpath_file))

    install_game_libraries(game_folder)
    run_command(game_folder, "mvn", c("-q", "compile"))

    # blocky_momot needs the blocky_model module from the local Maven repository (~/.m2)
    # to list its libraries, so install it there (with the parent pom of the game)
    run_command(game_folder, "mvn", c("-q", "install", "-pl", "blocky_model", "-am", "-Dmaven.test.skip=true"))
    run_command(game_folder, "mvn", c("-q", "-f", "blocky_momot/pom.xml", "dependency:build-classpath", paste0("-Dmdep.outputFile=", classpath_file)))

    classes <- file.path(game_folder, c("blocky_game", "blocky_momot", "blocky_model"), "target", "classes")
    paste(c(classes, readLines(classpath_file, warn = FALSE)), collapse = .Platform$path.sep)
}

# Run the Java tool on one session file, and return the metrics of its models
run_java_tool <- function(game_folder, classpath, session_file) {
    # Full path, because Java runs from the game folder
    session_file <- normalizePath(session_file)
    events_csv <- tempfile(fileext = ".csv")
    on.exit(unlink(events_csv))

    run_command(game_folder, "java", c("-cp", classpath, "blocky_game.ReportGoalChecker", session_file, "--csv", events_csv))

    # Read everything as text, to save the values exactly as the tool wrote them
    events <- read_csv(events_csv, col_types = cols(.default = col_character()))
    select(events, all_of(model_columns))
}

# Run a command from `folder`, and stop if it fails
run_command <- function(folder, command, args) {
    if (Sys.which(command) == "") {
        stop(command, " not found: install it and make sure it is on the PATH")
    }

    previous_folder <- setwd(folder) # Because the blocky_game is launched from another folder.
    on.exit(setwd(previous_folder))  # Restore the R working directory to proceed with this "replication" folder.

    status <- system2(command, shQuote(args))
    if (status != 0) {
        stop("The command failed (", status, "): ", command, " ", paste(args, collapse = " "))
    }
}
