#
# Exports the functions that count and draw how many of the goal-reaching models are
# non-dominated (Pareto-optimal in power and memory):
# - count_pareto_all(), list_pareto_all() and plot_pareto_all(): all participants together
# - count_pareto() and plot_pareto(): per level and variant (control or treatment)
#
# The labels come from the ground truth (ground_truth/run.py label, in data/labels.csv):
# - optimal: no program the game can build has fewer or equal actions and blocks, and fewer of one
# - dominated: such a program exists
# - not covered: the model has more blocks than the search tables, so it is unknown
# - no goal: the model does not reach the goal (only loaded candidates, see exploration.R)
# Only program runs are counted: every run, also the same program run twice.
#

# The labels, with readable names and colors. Also used by exploration.R.
pareto_labels <- c(
    optimal       = "Non-dominated",
    dominated     = "Dominated",
    "not covered" = "Not covered (unknown)",
    "no goal"     = "Does not reach the goal"
)
pareto_colors <- c("#1baf7a", "#d64545", "#a0a0a0", "#555555")

# The labels of a goal-reaching model
goal_labels <- c("optimal", "dominated", "not covered")

# Count the goal-reaching models of every label, per group of `...` (columns of `goal`), with the
# percentage of non-dominated models among all of them and among the decided ones (without "not covered")
count_labels <- function(goal, ...) {
    goal <- mutate(goal, pareto = factor(pareto, levels = goal_labels))
    counts <- count(goal, ..., pareto, .drop = FALSE)
    counts <- pivot_wider(counts, names_from = pareto, values_from = n, values_fill = 0)
    counts <- rename(counts, n_optimal = optimal, n_dominated = dominated, n_not_covered = "not covered")

    counts <- mutate(
        counts,
        n_goal_models = n_optimal + n_dominated + n_not_covered,
        percent_optimal = round(100 * n_optimal / n_goal_models, 1),
        percent_optimal_of_decided = round(100 * n_optimal / (n_optimal + n_dominated), 1)
    )
    select(counts, ..., n_goal_models, n_optimal, n_dominated, n_not_covered,
           percent_optimal, percent_optimal_of_decided)
}

# For each level, and for all levels together (row "all"): the goal-reaching models of every label
count_pareto_all <- function(goal) {
    goal <- mutate(goal, level = as.character(level))
    goal <- bind_rows(goal, mutate(goal, level = "all"))

    counts <- count_labels(goal, level)
    arrange(counts, level == "all", as.numeric(replace(level, level == "all", NA)))
}

# For each level and variant: the goal-reaching models of every label
count_pareto <- function(goal) {
    arrange(count_labels(goal, level, variant), level, variant)
}

# One row per goal-reaching run: the program, its actions and blocks, its label and the program
# of the known front closest to it that dominates it (empty if it is not dominated)
list_pareto_all <- function(goal, labels) {
    dominators <- select(labels, session, index, starts_with("dominatedBy"))
    goal <- left_join(goal, dominators, by = c("session", "index"))
    goal <- select(goal, level, variant, session, timestamp, program,
                   actions = power_kW_actions, blocks = memory_kB_blocks, pareto,
                   dominated_by = dominatedBy, dominated_by_actions = dominatedBy_actions,
                   dominated_by_blocks = dominatedBy_blocks)
    # session and timestamp last, so the order does not depend on the order the files are read in
    arrange(goal, level, pareto, actions, blocks, session, timestamp)
}

# Stacked bars of the goal-reaching models per level, colored by label, and save it to `file`.
plot_pareto_all <- function(goal, file = "plots/models_pareto_all.png") {
    p <- plot_labels(goal) +
        labs(title = "Goal-reaching models, all participants", x = "Level", y = "Models", fill = NULL)

    ggsave(file, p, width = 5, height = 3.5, dpi = 150, create.dir = TRUE)
}

# Stacked bars of the goal-reaching models per level, colored by label, one panel per variant,
# and save it to `file`.
plot_pareto <- function(goal, file = "plots/models_pareto.png") {
    p <- plot_labels(goal) +
        facet_wrap(~variant) +
        labs(title = "Goal-reaching models: dominated or not", x = "Level", y = "Models", fill = NULL)

    ggsave(file, p, width = 7, height = 3.5, dpi = 150, create.dir = TRUE)
}

# The stacked bars shared by the two plots above
plot_labels <- function(goal) {
    goal <- mutate(goal, label = factor(pareto_labels[pareto], levels = pareto_labels))

    ggplot(goal, aes(x = factor(level), fill = label)) +
        geom_bar(width = 0.7) +
        scale_y_continuous(breaks = function(limits) seq(0, limits[2], by = 2)) +
        scale_fill_manual(values = setNames(pareto_colors, pareto_labels)) +
        theme_minimal() +
        theme(legend.position = "bottom")
}
