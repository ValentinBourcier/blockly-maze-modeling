#
# Exports the functions that draw the plots: 
# - per_level and its compact version, for the paper
# - the distributions ones to study the distribution and decide what to report, the mean or median.
#

# Readable names of the metrics, in the order of the panels.
# Also used by table.R for the rows of the LaTeX table.
metric_labels <- c(
    level_runs        = "Program runs",
    level_elapsed_min = "Time on level (min)",
    clem_searches     = "CLEM searches",
    solutions_loaded  = "Solutions loaded",
    min_power_kW      = "Min power to win (kW)",
    min_memory_kB     = "Min memory to win (kB)"
)

# Draw every metric per level, one small plot per metric, and save it to `file`.
plot_per_level <- function(per_level, file = "plots/per_level.png") {
    # One row per level, variant, metric and statistic:
    # the column "level_runs_mean" becomes metric = "level_runs" and statistic = "mean"
    long <- pivot_longer(
        per_level,
        cols = -c(level, variant, n_sessions),
        names_to = c("metric", "statistic"),
        names_pattern = "(.*)_(mean|median)",
        values_to = "value"
    )

    # Readable metric names, in the order of metric_labels
    long <- mutate(long, metric = factor(metric, levels = names(metric_labels), labels = metric_labels))

    p <- ggplot(long, aes(x = level, y = value, color = variant, linetype = statistic, shape = statistic)) +
        geom_line() +
        geom_point() +
        facet_wrap(~metric, scales = "free_y", nrow = 2) +
        expand_limits(y = 0) +
        scale_x_continuous(breaks = 1:10) +
        scale_y_continuous(n.breaks = 10) +
        scale_color_manual(values = c(control = "#2a78d6", treatment = "#eb6834")) +
        labs(x = "Level", y = NULL, color = "Variant", linetype = "Statistic", shape = "Statistic") +
        theme_minimal() +
        theme(legend.position = "bottom")

    ggsave(file, p, width = 10, height = 6, dpi = 150, create.dir = TRUE)
}

# Draw the main metrics per level in four small panels, and save it to `file`:
# - program runs, min power to win and min memory to win: control vs treatment
# - how treatment players used CLEM (searches and solutions loaded)
# Each statistic chooses what its panels draw, "mean" or "median":
# - `runs_statistic`: program runs
# - `clem_statistic`: CLEM usage
# - `models_statistic`: min power and min memory to win
plot_per_level_compact <- function(per_level, runs_statistic = "median", clem_statistic = "mean", models_statistic = "median",
                                   file = paste0("plots/per_level_compact_", runs_statistic, "_", clem_statistic, "_", models_statistic, ".png")) {
    control   <- filter(per_level, variant == "control")
    treatment <- filter(per_level, variant == "treatment")

    # Panel titles, with the statistic each panel shows
    runs   <- paste0("Program runs (", runs_statistic, ")")
    clem   <- paste0("Treatment: CLEM usage (", clem_statistic, ")")
    power  <- paste0("Min power to win, kW (", models_statistic, ")")
    memory <- paste0("Min memory to win, kB (", models_statistic, ")")

    # One line of the plot: the chosen statistic of `metric` for each level
    one_line <- function(data, metric, statistic, line, panel) {
        tibble(level = data$level, value = data[[paste0(metric, "_", statistic)]], line = line, panel = panel)
    }

    lines <- bind_rows(
        one_line(control,   "level_runs",       runs_statistic,   "Control",          runs),
        one_line(treatment, "level_runs",       runs_statistic,   "Treatment",        runs),
        one_line(treatment, "clem_searches",    clem_statistic,   "CLEM searches",    clem),
        one_line(treatment, "solutions_loaded", clem_statistic,   "Solutions loaded", clem),
        one_line(control,   "min_power_kW",     models_statistic, "Control",          power),
        one_line(treatment, "min_power_kW",     models_statistic, "Treatment",        power),
        one_line(control,   "min_memory_kB",    models_statistic, "Control",          memory),
        one_line(treatment, "min_memory_kB",    models_statistic, "Treatment",        memory)
    )

    # Same blue and orange as the other plots for control and treatment,
    # green and violet for the CLEM metrics
    line_colors <- c(
        "Control"          = "#2a78d6",
        "Treatment"        = "#eb6834",
        "CLEM searches"    = "#1baf7a",
        "Solutions loaded" = "#6250d6"
    )
    lines <- mutate(
        lines,
        line  = factor(line, levels = names(line_colors)),
        panel = factor(panel, levels = c(runs, clem, power, memory))
    )

    p <- ggplot(lines, aes(x = level, y = value, color = line)) +
        geom_line() +
        geom_point(size = 2) +
        facet_wrap(~panel, ncol = 2, scales = "free_y") +
        expand_limits(y = 0) +
        scale_x_continuous(breaks = 1:10) +
        scale_y_continuous(n.breaks = 10) +
        scale_color_manual(values = line_colors) +
        labs(title = "Per level", x = "Level", y = NULL, color = NULL) +
        theme_minimal() +
        theme(legend.position = "bottom")

    ggsave(file, p, width = 7, height = 6, dpi = 150, create.dir = TRUE)
}

# Boxplot of one metric per level and variant, to see the spread behind the
# mean and the median, and save it to `file`.
# Each dot is one session. The line in the box is the median, the black x is the mean.
# If the x sits far from the line, the mean is pulled by outliers.
# `variants` chooses which variants to show, e.g. "treatment" alone.
plot_distribution <- function(sessions, column, variants = c("control", "treatment"), file = paste0("plots/distributions/distribution_", column, ".png")) {
    sessions <- filter(sessions, variant %in% variants)

    # Boxes and dots of the two variants side by side, at each level
    side_by_side <- position_dodge(width = 0.8)
    scattered    <- position_jitterdodge(jitter.width = 0.15, dodge.width = 0.8, seed = 1)

    p <- ggplot(sessions, aes(x = factor(level), y = .data[[column]], color = variant, fill = variant)) +
        geom_boxplot(position = side_by_side, width = 0.6, alpha = 0.15, outlier.shape = NA) +
        geom_point(position = scattered, size = 1.2, alpha = 0.6) +
        stat_summary(aes(group = variant), fun = mean, geom = "point", shape = 4, size = 3, stroke = 1.2,
                     color = "grey10", position = side_by_side, show.legend = FALSE) +
        scale_y_continuous(n.breaks = 10) +
        scale_color_manual(values = c(control = "#2a78d6", treatment = "#eb6834"), aesthetics = c("color", "fill")) +
        labs(title = metric_labels[[column]], subtitle = "Box: median and quartiles, x: mean, dots: sessions",
             x = "Level", y = NULL, color = "Variant", fill = "Variant") +
        theme_minimal() +
        theme(legend.position = "bottom")

    ggsave(file, p, width = 6, height = 3.5, dpi = 150, create.dir = TRUE)
}
