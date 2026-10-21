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
source("compute_labels.R")
source("read.R")
source("group.R")
source("plot.R")
source("table.R")
source("questionnaire.R")
source("pareto.R")
source("exploration.R")
source("questionnaire.R")
source("pareto.R")
source("exploration.R")
source("performance.R")

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
write_csv(per_level, "tables/per_level.csv") # e.g. the median time on level 10, per variant

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

# Control vs treatment, one value per participant (mean over their levels):
# median per variant, exact Wilcoxon test and Cliff's delta
participants <- per_participant(sessions)
print(participants, width = Inf)
write_csv(participants, "tables/performance_per_participant.csv")

performance <- performance_test(participants)
print(performance, width = Inf)
write_csv(performance, "tables/performance_test.csv")

# Treatment: who used the CLEM exploration on levels 5 to 9, and on level 10
exploration_participants <- count_exploration_participants(filter(sessions, variant == "treatment"))
print(exploration_participants, width = Inf)
write_csv(exploration_participants, "tables/exploration_per_participant.csv")


answers <- read_questionnaire()

sus <- sus_scores(answers)
print(sus, n = Inf)
write_csv(sus, "tables/sus_scores.csv")

sus_stats <- sus_summary(sus)
print(sus_stats, width = Inf)
write_csv(sus_stats, "tables/sus_summary.csv")

sus_comparison <- sus_test(sus)
print(sus_comparison, width = Inf)
write_csv(sus_comparison, "tables/sus_test.csv")
# Label every model as non-dominated or not with the ground truth (Python),
# in data/labels.csv (only if the models are not labelled yet)
compute_labels()
labels <- read_labels()
labelled <- add_labels(models, labels)

# The goal-reaching program runs: how many are non-dominated, per level,
# for all participants together and per variant
goal <- filter(labelled, type == "program_run", status == "WON")

pareto_all <- count_pareto_all(goal)
print(pareto_all, n = Inf, width = Inf)
write_csv(pareto_all, "tables/models_pareto_all.csv")
write_csv(list_pareto_all(goal, labels), "tables/models_pareto_all_models.csv", na = "")
plot_pareto_all(goal)

pareto <- count_pareto(goal)
print(pareto, n = Inf, width = Inf)
write_csv(pareto, "tables/models_pareto.csv")
plot_pareto(goal)

# Treatment: the models loaded from the CLEM exploration, and whether the participants ran them
loaded <- loaded_models(filter(labelled, variant == "treatment"))
print(loaded, n = Inf, width = Inf)
write_csv(loaded, "tables/exploration_models.csv")

exploration <- count_exploration(filter(sessions, variant == "treatment"), loaded)
print(exploration, n = Inf, width = Inf)
write_csv(exploration, "tables/exploration_per_level.csv")
plot_exploration(loaded)

answers <- read_questionnaire()

sus <- sus_scores(answers)
print(sus, n = Inf)
write_csv(sus, "tables/sus_scores.csv")

sus_stats <- sus_summary(sus)
print(sus_stats, width = Inf)
write_csv(sus_stats, "tables/sus_summary.csv")

sus_comparison <- sus_test(sus)
print(sus_comparison, width = Inf)
write_csv(sus_comparison, "tables/sus_test.csv")
# Label every model as non-dominated or not with the ground truth (Python),
# in data/labels.csv (only if the models are not labelled yet)
compute_labels()
labels <- read_labels()
labelled <- add_labels(models, labels)

# The goal-reaching program runs: how many are non-dominated, per level,
# for all participants together and per variant
goal <- filter(labelled, type == "program_run", status == "WON")

pareto_all <- count_pareto_all(goal)
print(pareto_all, n = Inf, width = Inf)
write_csv(pareto_all, "tables/models_pareto_all.csv")
write_csv(list_pareto_all(goal, labels), "tables/models_pareto_all_models.csv", na = "")
plot_pareto_all(goal)

pareto <- count_pareto(goal)
print(pareto, n = Inf, width = Inf)
write_csv(pareto, "tables/models_pareto.csv")
plot_pareto(goal)

# Treatment: the models loaded from the CLEM exploration, and whether the participants ran them
loaded <- loaded_models(filter(labelled, variant == "treatment"))
print(loaded, n = Inf, width = Inf)
write_csv(loaded, "tables/exploration_models.csv")

exploration <- count_exploration(filter(sessions, variant == "treatment"), loaded)
print(exploration, n = Inf, width = Inf)
write_csv(exploration, "tables/exploration_per_level.csv")
plot_exploration(loaded)
