#
# Exports the functions that read the data of the experiment, already cleaned:
# - read_sessions(): the session files, one row per session and level
# - read_models(): the models files computed by compute_models(), one row per model
# - read_labels(): the Pareto labels of the models computed by compute_labels(), one row per model
# - add_labels(): adds these labels to the models
#

# Read every .json session file of `folder` and return one clean table
# with one row per session and level.
read_sessions <- function(folder = "data") {
    files <- list.files(folder, pattern = "\\.json$", full.names = TRUE)
    if (length(files) == 0) {
        stop("No .json file found in the folder '", folder, "'")
    }

    sessions <- bind_rows(lapply(files, read_levels))
    sessions <- clean(sessions)
    arrange(sessions, session, level)
}

# Read every models CSV file of `folder` and return one clean table
# with one row per model (program run or CLEM candidate).
read_models <- function(folder = "data/models") {
    files <- list.files(folder, pattern = "\\.csv$", full.names = TRUE)
    if (length(files) == 0) {
        stop("No models file found in '", folder, "': run compute_models() first")
    }

    models <- bind_rows(lapply(files, read_csv, show_col_types = FALSE))
    clean(models)
}

# Read the Pareto label of every model, one row per model, with the columns of read_models()
# plus `pareto`: "optimal" (no program beats it), "dominated" (a program beats it),
# "not covered" (bigger than the search tables: unknown) or "no goal", and the program that
# dominates it (`dominatedBy`, `dominatedBy_actions`, `dominatedBy_blocks`).
read_labels <- function(file = "data/labels.csv") {
    if (!file.exists(file)) {
        stop("No labels in '", file, "': run compute_labels() first")
    }

    labels <- read_csv(file, show_col_types = FALSE)
    clean(labels)
}

# Add the Pareto label of read_labels() to every model, matched by session and index.
# Stops if the labels do not match the models (labels computed on other data).
add_labels <- function(models, labels) {
    models <- left_join(models, select(labels, session, index, pareto), by = c("session", "index"))
    if (anyNA(models$pareto) || nrow(models) != nrow(labels)) {
        stop("The labels do not match the models: delete data/labels.csv and run compute_labels() again")
    }
    models
}

# Read the "levels" of one session file: one row per level.
# The time spent on the level is converted from milliseconds to minutes.
read_levels <- function(file) {
    json <- fromJSON(file)

    rows <- lapply(names(json$levels), function(level) {
        info <- json$levels[[level]]
        tibble(
            session               = json$sessionId,
            user                  = json$userId,
            level                 = as.integer(level),
            variant               = info$variant,
            level_runs            = info$programRuns,
            level_elapsed_min     = info$elapsedMs / 60000,
            clem_searches         = info$momotSearches,
            solutions_loaded      = info$solutionsLoaded
        )
    })
    bind_rows(rows)
}

# The cleaning shared by the sessions and the models:
# - remove the first 4 levels, used for training
# - rename the variants: 'momot' -> 'treatment', 'original' -> 'control'
# - make the variant categorical, with control listed first
clean <- function(data) {
    data <- filter(data, level > 4)
    mutate(
        data,
        variant = replace_values(variant, "momot" ~ "treatment", "original" ~ "control"),
        variant = factor(variant, levels = c("control", "treatment"))
    )
}
