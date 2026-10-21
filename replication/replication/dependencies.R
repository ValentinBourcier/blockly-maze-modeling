# Run this once before launching main.R:
#   Rscript dependencies.R
#
# dplyr 1.2.0 or newer is needed (for recode_values and replace_values).

install.packages(
    c("readr", "dplyr", "tidyr", "ggplot2", "jsonlite"),
    repos = "https://cloud.r-project.org"
)
