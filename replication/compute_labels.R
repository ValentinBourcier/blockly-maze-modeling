#
# Exports one function, compute_labels(), that runs the ground-truth tool (ground_truth/run.py)
# to label every model as non-dominated or not, and saves the labels in data/labels.csv.
# read_labels(), in read.R, reads this file back.
#
# Labelling needs Python 3 and the ground-truth tables of ground_truth/tables/ (provided).
# Reading only needs data/labels.csv, so the analysis can be reproduced without Python.
# run_command() comes from compute_models.R.
#

# Label the models of `data_folder` (in its models/ subfolder), in data_folder/labels.csv.
# Skipped if the labels already exist: delete the file to label again (new sessions, new tables).
compute_labels <- function(data_folder = "data") {
    labels_file <- file.path(data_folder, "labels.csv")
    if (file.exists(labels_file)) {
        message("The models are already labelled in ", labels_file)
        return(invisible(NULL))
    }

    # python on Windows (where python3 is often a stub of the Microsoft Store), python3 elsewhere
    python <- if (.Platform$OS.type == "windows") "python" else "python3"
    message("Labelling the models of ", data_folder)
    run_command(".", python, c("ground_truth/run.py", "label", "--sessions", data_folder))
}
