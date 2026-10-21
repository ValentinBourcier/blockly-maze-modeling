#
# Counts, for each level and condition (control or treatment), how many of the models
# that reach the goal are non-dominated (Pareto-optimal in power and memory), and draws it.
# Run it from this folder (tools/ground-truth-analysis/reports): Rscript models_pareto.R
#
# The labels come from the ground-truth tool (tools/ground-truth-analysis, step "label"):
# - optimal: no program the game can build has fewer or equal actions and blocks, and fewer of one
# - dominated: such a program exists
# - not covered: the model has more blocks than the search tables, so it is unknown
# Only program runs are counted, as in models_reached.R: every run, also the same program run twice.
#

library(readr)
library(jsonlite)
library(dplyr)
library(tidyr)
library(ggplot2)

source("study.R") # the data folders, and where the replication package is
source(file.path(replication_folder, "read.R"))

# The labels, with readable names and colors
pareto_labels <- c(
    optimal       = "Non-dominated",
    dominated     = "Dominated",
    "not covered" = "Not covered (unknown)"
)
pareto_colors <- c("#1baf7a", "#d64545", "#a0a0a0")

# For each level and condition: the goal-reaching models of every label, and the percentage
# of non-dominated models among all of them and among the decided ones (without "not covered")
count_pareto <- function(goal) {
    goal <- mutate(goal, pareto = factor(pareto, levels = names(pareto_labels)))
    counts <- count(goal, level, variant, pareto, .drop = FALSE)
    counts <- pivot_wider(counts, names_from = pareto, values_from = n, values_fill = 0)
    counts <- rename(counts, n_optimal = optimal, n_dominated = dominated, n_not_covered = "not covered")

    counts <- mutate(
        counts,
        n_goal_models = n_optimal + n_dominated + n_not_covered,
        percent_optimal = round(100 * n_optimal / n_goal_models, 1),
        percent_optimal_of_decided = round(100 * n_optimal / (n_optimal + n_dominated), 1)
    )
    counts <- select(counts, level, variant, n_goal_models, n_optimal, n_dominated, n_not_covered,
                     percent_optimal, percent_optimal_of_decided)
    arrange(counts, level, variant)
}

# Stacked bars of the goal-reaching models per level, colored by label, one panel per condition,
# and save it to `file`.
plot_pareto <- function(goal, file = "plots/models_pareto.png") {
    goal <- mutate(goal, label = factor(pareto_labels[pareto], levels = pareto_labels))

    p <- ggplot(goal, aes(x = factor(level), fill = label)) +
        geom_bar(width = 0.7) +
        facet_wrap(~variant) +
        scale_y_continuous(breaks = function(limits) seq(0, limits[2], by = 2)) +
        scale_fill_manual(values = setNames(pareto_colors, pareto_labels)) +
        labs(title = "Goal-reaching models: dominated or not", x = "Level", y = "Models", fill = NULL) +
        theme_minimal() +
        theme(legend.position = "bottom")

    ggsave(file, p, width = 7, height = 3.5, dpi = 150, create.dir = TRUE)
}

study <- read_study()
models <- add_labels(study$models, read_labels())
goal <- filter(models, type == "program_run", status == "WON")

counts <- count_pareto(goal)
print(counts, n = Inf, width = Inf)

dir.create("tables", showWarnings = FALSE)
write_csv(counts, "tables/models_pareto.csv")
plot_pareto(goal)
