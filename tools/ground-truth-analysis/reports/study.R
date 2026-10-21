#
# Exports three functions:
# - read_study(): reads the sessions and the models of the user study, stored in one
#   folder of session files per condition (control or treatment)
# - read_labels(): reads the Pareto labels of the models, computed by the ground-truth tool
# - add_labels(): adds these labels to the models
# The scripts run from this folder (tools/ground-truth-analysis/reports) and source this file
# first, then the functions of the replication package from `replication_folder`.
#
# The order of the pipeline:
# 1. Rscript build_models.R: replays the sessions, one CSV per session in <folder>/models/
# 2. python ../run.py label --sessions <folder>: labels the models, in ../output/<folder>_labelled.csv
# 3. the reports (goal_reached.R, ...): read the sessions, the models and the labels
#

# Folder of the replication package (read.R, compute_models.R, group.R, plot.R)
replication_folder <- "../../../replication"

# Folder of the game repository, used by build_models.R to replay the sessions with Java
game_folder <- "../../.."

# One folder of session files per condition. The condition of a session is read from
# its file (variant 'original' = control, 'momot' = treatment); the names only check it.
session_folders <- c(
    control   = "../control_user_report",
    treatment = "../treatment_user_report"
)

# Read the sessions and the models of every condition folder.
# Returns a list: `sessions` (one row per session and level) and `models` (one row per model).
read_study <- function(folders = session_folders) {
    conditions <- lapply(names(folders), \(condition) read_condition(condition, folders[[condition]]))
    list(
        sessions = bind_rows(lapply(conditions, \(x) x$sessions)),
        models   = bind_rows(lapply(conditions, \(x) x$models))
    )
}

# Read the sessions and the models of one condition folder.
# The models are read from <folder>/models/, written by build_models.R.
read_condition <- function(condition, folder) {
    models_folder <- file.path(folder, "models")
    if (!dir.exists(models_folder)) {
        stop("No models in '", models_folder, "': run build_models.R first")
    }

    sessions <- read_sessions(folder)
    models <- read_models(models_folder)

    if (any(sessions$variant != condition)) {
        stop("The folder '", folder, "' holds sessions that are not ", condition)
    }
    list(sessions = sessions, models = models)
}

# Folder of the labelled models written by the ground-truth tool:
#   python ../run.py label --sessions <condition folder>
# writes <labels_folder>/<condition folder name>_labelled.csv
labels_folder <- "../output"

# Read the Pareto label of every model, one row per model, with the columns of read_models()
# plus `pareto`: "optimal" (no program beats it), "dominated" (a program beats it),
# "not covered" (bigger than the search tables: unknown) or "no goal".
read_labels <- function(folders = session_folders) {
    files <- file.path(labels_folder, paste0(basename(folders), "_labelled.csv"))
    missing <- files[!file.exists(files)]
    if (length(missing) > 0) {
        stop("No labels in ", paste(missing, collapse = ", "), ": run the label step of tools/ground-truth-analysis first")
    }

    labels <- bind_rows(lapply(files, read_csv, show_col_types = FALSE))
    clean(labels)
}

# Add the Pareto label of read_labels() to every model, matched by session and index.
# Stops if the labels do not match the models (labels computed on other data).
add_labels <- function(models, labels) {
    models <- left_join(models, select(labels, session, index, pareto), by = c("session", "index"))
    if (anyNA(models$pareto) || nrow(models) != nrow(labels)) {
        stop("The labels do not match the models: run the label step of tools/ground-truth-analysis again")
    }
    models
}
