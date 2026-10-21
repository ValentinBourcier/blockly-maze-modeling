#
# Exports the functions that compare the performance of the control and treatment
# participants, with one value per participant (5 per variant):
# - per_participant(): the metrics of every participant, averaged over their levels
# - performance_test(): the median of every metric per variant, and the test between them
#
# The test uses exact_wilcoxon_p() and cliffs_delta(), defined in questionnaire.R.
#

# The metrics compared between the variants
performance_metrics <- c("level_runs", "level_elapsed_min", "min_power_kW", "min_memory_kB")

# One row per participant: the mean of every metric over their levels (5 to 10).
# A level without any winning program has no min power and memory: it is ignored.
per_participant <- function(sessions) {
    grouped <- group_by(sessions, session, user, variant)
    participants <- summarise(
        grouped,
        n_levels = n(),
        across(all_of(performance_metrics), mean_value),
        .groups = "drop"
    )
    arrange(participants, variant, user)
}

# For every metric: the median over the participants of each variant, the two-sided
# exact Wilcoxon-Mann-Whitney test between the variants, and Cliff's delta
# (treatment vs control: negative when the treatment values are lower).
performance_test <- function(participants) {
    rows <- lapply(performance_metrics, function(metric) {
        control   <- participants[[metric]][participants$variant == "control"]
        treatment <- participants[[metric]][participants$variant == "treatment"]
        tibble(
            metric           = metric,
            n_control        = length(control),
            n_treatment      = length(treatment),
            control_median   = median(control),
            treatment_median = median(treatment),
            wilcoxon_p       = exact_wilcoxon_p(treatment, control),
            cliffs_delta     = cliffs_delta(treatment, control)
        )
    })
    tests <- bind_rows(rows)
    mutate(tests, across(where(is.numeric), \(x) round(x, 3)))
}
