#
# Exports three functions:
# - add_best_models(): adds to the sessions the metrics of their best winning programs
# - aggregate(): summarises the sessions per level and variant
# - aggregate_overall(): summarises the sessions over all the levels, per variant
#

# The metrics to summarise
metrics <- c(
    "level_runs", "level_elapsed_min", "clem_searches", "solutions_loaded",
    "min_power_kW", "min_memory_kB"
)

# Add to every session and level the smallest power and the smallest memory among the
# programs the player ran that reach the goal (status WON).
# CLEM candidates are not counted: a candidate the player ran is already a program run.
# A level without any winning program gets NA.
add_best_models <- function(sessions, models) {
    won <- filter(models, type == "program_run", status == "WON")

    grouped <- group_by(won, session, level)
    best <- summarise(
        grouped,
        min_power_kW  = min(power_kW_actions),
        min_memory_kB = min(memory_kB_blocks),
        .groups = "drop"
    )
    left_join(sessions, best, by = c("session", "level"))
}

# For each level and variant, compute the mean and the median of every metric.
# Each metric gives two columns, e.g. level_runs_mean and level_runs_median.
aggregate <- function(data) {
    grouped <- group_by(data, level, variant)
    per_level <- summarise(
        grouped,
        n_sessions = n(),
        across(all_of(metrics), list(mean = mean_value, median = median_value), .names = "{.col}_{.fn}"),
        .groups = "drop"
    )
    arrange(per_level, level, variant)
}

# Over all the levels, compute the mean and the median of every metric.
# One row per metric, one column per variant and statistic:
#   metric | control_mean | control_median | treatment_mean | treatment_median

aggregate_overall <- function(data) {
    long <- pivot_longer(data, cols = all_of(metrics), names_to = "metric", values_to = "value")

    grouped <- group_by(long, metric, variant)
    overall <- summarise(grouped, mean = mean_value(value), median = median_value(value), .groups = "drop")

    overall <- pivot_wider(
        overall,
        names_from = variant,
        values_from = c(mean, median),
        names_glue = "{variant}_{.value}"
    )

    # Rows in the order of `metrics`, values rounded to 2 decimals
    overall <- arrange(overall, match(metric, metrics))
    overall <- mutate(overall, across(where(is.numeric), \(x) round(x, 2)))
    select(overall, metric, control_mean, control_median, treatment_mean, treatment_median)
}

# Mean and median that ignore missing values (a level without any winning program)
mean_value   <- function(x) mean(x, na.rm = TRUE)
median_value <- function(x) median(x, na.rm = TRUE)
