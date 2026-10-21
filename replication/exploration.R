#
# Exports the functions that count and draw the models the treatment participants loaded from
# the CLEM exploration: whether they reach the goal and are non-dominated, and whether the
# participants ran them.
# - loaded_models(): one row per loaded model
# - count_exploration(): per level, the searches, loads and loaded models by outcome
# - count_exploration_participants(): per participant, the searches and loads before level 10 and on it
# - plot_exploration(): the loaded models per level, by label
#
# A loaded model is one program loaded by one participant on one level: loading the same
# program again counts once (n_loads counts every load). A participant "ran it after" the load
# if they ran the same program later on that level, and "built it before" if they had already
# run it earlier. A loaded program that was edited before running counts as not run.
# The labels and their colors (pareto_labels, pareto_colors) come from pareto.R.
#

# One row per loaded model, with its metrics, label, number of loads, and whether the
# participant ran the same program before the first load or after it
loaded_models <- function(models) {
    loads <- filter(models, type == "candidate_exploration")
    runs  <- filter(models, type == "program_run")

    grouped <- group_by(loads, session, level, program, status, power_kW_actions, memory_kB_blocks, pareto)
    loaded <- summarise(grouped, n_loads = n(), first_load = min(timestamp), .groups = "drop")

    # Every run of the same program by the same participant on the same level
    runs <- select(runs, session, level, program, run_time = timestamp)
    joined <- left_join(loaded, runs, by = c("session", "level", "program"), relationship = "many-to-many")
    grouped <- group_by(joined, session, level, program)
    runs_around <- summarise(
        grouped,
        built_before = any(run_time < first_load, na.rm = TRUE),
        run_after    = any(run_time > first_load, na.rm = TRUE),
        .groups = "drop"
    )

    loaded <- left_join(loaded, runs_around, by = c("session", "level", "program"))
    arrange(loaded, level, session, first_load)
}

# For each level: the CLEM searches and loads of the session files, and the loaded models
# by outcome. Stops if the loads of the session files differ from the loaded models.
count_exploration <- function(sessions, loaded) {
    grouped <- group_by(sessions, level)
    per_level <- summarise(
        grouped,
        n_participants        = n(),
        participants_searched = sum(clem_searches > 0),
        clem_searches         = sum(clem_searches),
        participants_loaded   = sum(solutions_loaded > 0),
        n_loads               = sum(solutions_loaded),
        .groups = "drop"
    )

    grouped <- group_by(loaded, level)
    per_model <- summarise(
        grouped,
        n_loads_logged   = sum(n_loads),
        n_loaded_models  = n(),
        n_goal           = sum(status == "WON"),
        n_non_dominated  = sum(pareto == "optimal"),
        n_run_after_load = sum(run_after),
        n_built_before   = sum(built_before),
        .groups = "drop"
    )

    counts <- left_join(per_level, per_model, by = "level")
    counts <- mutate(counts, across(n_loads_logged:n_built_before, \(x) coalesce(x, 0L)))
    if (any(counts$n_loads != counts$n_loads_logged)) {
        stop("The loads counted in the session files differ from the loaded models")
    }
    select(counts, -n_loads_logged)
}

# One row per treatment participant: their CLEM searches and loads on levels 5 to 9,
# and on level 10. Tells whether the same participants explored on several levels.
count_exploration_participants <- function(sessions) {
    sessions <- mutate(sessions, levels = if_else(level == 10, "level_10", "levels_5_9"))
    grouped <- group_by(sessions, user, levels)
    counts <- summarise(grouped, searches = sum(clem_searches), loads = sum(solutions_loaded), .groups = "drop")
    counts <- pivot_wider(counts, names_from = levels, values_from = c(searches, loads), names_glue = "{.value}_{levels}")
    select(counts, user, searches_levels_5_9, searches_level_10, loads_levels_5_9, loads_level_10)
}

# Stacked bars of the loaded models per level, colored by label, and save it to `file`.
# Levels without any load are shown empty.
plot_exploration <- function(loaded, levels = 5:10, file = "plots/exploration_models.png") {
    loaded <- mutate(
        loaded,
        level = factor(level, levels = levels),
        label = factor(pareto_labels[pareto], levels = pareto_labels)
    )

    p <- ggplot(loaded, aes(x = level, fill = label)) +
        geom_bar(width = 0.7) +
        scale_x_discrete(drop = FALSE) +
        scale_y_continuous(breaks = function(limits) seq(0, ceiling(limits[2]))) +
        scale_fill_manual(values = setNames(pareto_colors, pareto_labels)) +
        labs(title = "Treatment: models loaded from the CLEM exploration",
             subtitle = "One model = one program loaded by one participant on one level",
             x = "Level", y = "Loaded models", fill = NULL) +
        theme_minimal() +
        theme(legend.position = "bottom")

    ggsave(file, p, width = 6, height = 3.5, dpi = 150, create.dir = TRUE)
}
