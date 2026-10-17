#
# Reproduces the diagrams of the experiment.
# Run it from the project folder: Rscript main.R
#

library(readr)
library(jsonlite)
library(dplyr)
library(tidyr)
library(ggplot2)

source("compute_models.R")
source("read.R")
source("group.R")
source("plot.R")
source("table.R")

# Folder of the game repository (with blocky_game, blocky_momot and blocky_model),
# only needed to compute the models of new sessions with Java. Leave NA without it.
game_folder <- ".."

# Compute the metrics of every model (program) with Java,
# one CSV file per session in data/models/ (only for new sessions)
if (!is.na(game_folder)) compute_models(game_folder)

# Read the sessions data: one row per session and level, and one row per model
sessions <- read_sessions()
print(sessions, n = Inf, width = Inf)

models <- read_models()
print(models, width = Inf)

# Add the min power and memory of the winning programs to every session and level
sessions <- add_best_models(sessions, models)

# Mean and median of every metric over all the levels, per variant
overall <- aggregate_overall(sessions)
print(overall, width = Inf)

dir.create("tables", showWarnings = FALSE)
write_csv(overall, "tables/overall.csv")
write_latex_table(overall) # tables/overall.tex, for the paper

# Mean and median of every metric, per level and variant
per_level <- aggregate(sessions)
print(per_level, n = Inf, width = Inf)
# write_csv(per_level, "experiment_data_per_level.csv")

# Plots for the paper: every metric per level
plot_per_level(per_level)

# Compact version: the three metrics in two panels, with the statistic of each panel: "mean" or "median"
plot_per_level_compact(per_level, runs_statistic = "median", clem_statistic = "mean", models_statistic = "median")

# Distributions: to check the spread of data using mean and median
plot_distribution(sessions, "level_runs")
plot_distribution(sessions, "level_elapsed_min")
plot_distribution(sessions, "clem_searches", variants = "treatment")
plot_distribution(sessions, "solutions_loaded", variants = "treatment")
plot_distribution(sessions, "min_power_kW")
plot_distribution(sessions, "min_memory_kB")
