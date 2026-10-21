

sus_items <- paste0("SUS", 1:10)

# Read the questionnaire file, where every row is a question and every column a
# participant, and return one row per participant.
read_questionnaire <- function(file = "data/questionnaire.csv") {
    raw <- read_csv(file, col_names = FALSE, col_types = cols(.default = col_character()))

    # Transpose: the first column holds the names of the questions
    answers <- as_tibble(t(raw[, -1]), .name_repair = "minimal")
    names(answers) <- raw[[1]]
    answers <- type_convert(answers, col_types = cols(ID = col_character(), .default = col_guess()))

    # Same variant names as the sessions (see clean() in read.R)
    mutate(
        answers,
        participant = ID,
        variant = replace_values(tolower(Group), "treatement" ~ "treatment"),
        variant = factor(variant, levels = c("control", "treatment")),
        .before = 1
    )
}

# prorate : mean of the answered items x 25 (Lewis & Sauro, 2017)
sus_scores <- function(answers) {
    items <- as.matrix(answers[sus_items])

    n_missing <- rowSums(is.na(items))
    if (any(n_missing > 1)) {
        stop("More than one SUS item is missing for: ", paste(answers$participant[n_missing > 1], collapse = ", "))
    }



    odd <- seq(1, 10, by = 2)
    contributions <- items
    contributions[, odd] <- items[, odd] - 1
    contributions[, -odd] <- 5 - items[, -odd]

    scores <- tibble(
        participant = answers$participant,
        variant     = answers$variant,
        n_missing   = n_missing,
        sus         = rowMeans(contributions, na.rm = TRUE) * 25,
    )
    
    arrange(scores, variant, desc(sus))
}


# The descriptive statistics of the SUS score, one row per variant.
sus_summary <- function(scores) {
    grouped <- group_by(scores, variant)
    summary <- summarise(
        grouped,
        n      = n(),
        mean   = mean(sus),
        sd     = sd(sus),
        # 95% confidence interval of the mean, with the t distribution (small samples)
        ci_low  = mean - qt(0.975, n - 1) * sd / sqrt(n),
        ci_high = mean + qt(0.975, n - 1) * sd / sqrt(n),
        median = median(sus),
        q1     = quantile(sus, 0.25),
        q3     = quantile(sus, 0.75),
        min    = min(sus),
        max    = max(sus),
        .groups = "drop"
    )
    mutate(summary, across(where(is.numeric), \(x) round(x, 2)))
}

# Compare the SUS score of the treatment and control variants, in one row:
# - the Wilcoxon-Mann-Whitney test, the main test since the groups are small (5 vs 5) and
#   the scores are bounded and maybe not normal. 
# - Two effect sizes: Cliff's delta (goes with Wilcoxon)
sus_test <- function(scores) {
    treatment <- scores$sus[scores$variant == "treatment"]
    control   <- scores$sus[scores$variant == "control"]

    welch <- t.test(treatment, control)

    tibble(
        n_control       = length(control),
        n_treatment     = length(treatment),
        mean_difference = mean(treatment) - mean(control),
        wilcoxon_p      = exact_wilcoxon_p(treatment, control),
        cliffs_delta    = cliffs_delta(treatment, control),
    )
}

exact_wilcoxon_p <- function(x, y) {
    ranks <- rank(c(x, y))
    splits <- combn(length(ranks), length(x))
    rank_sums <- colSums(matrix(ranks[splits], nrow = length(x)))

    expected <- length(x) * (length(ranks) + 1) / 2
    observed <- abs(sum(ranks[seq_along(x)]) - expected)
    # The tolerance avoids missing a split equal to the observed one by rounding
    mean(abs(rank_sums - expected) >= observed - 1e-9)
}


cliffs_delta <- function(x, y) {
    mean(sign(outer(x, y, "-")))
}
