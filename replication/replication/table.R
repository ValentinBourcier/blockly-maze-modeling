#
# Exports one function that writes the overall table as LaTeX code.
#

# Write the output of aggregate_overall() as a LaTeX tabular, to include in
# the paper with \input{tables/overall.tex} inside a table environment.
# Needs \usepackage{booktabs} in the paper.
# The metric names come from `metric_labels`, defined in plot.R.
write_latex_table <- function(overall, file = "tables/overall.tex") {
    # Numbers always with 2 decimals, e.g. 5.50
    number <- function(x) formatC(x, format = "f", digits = 2)

    rows <- paste(
        metric_labels[overall$metric],
        number(overall$control_mean),
        number(overall$control_median),
        number(overall$treatment_mean),
        number(overall$treatment_median),
        sep = " & "
    )

    lines <- c(
        "\\begin{tabular}{lrrrr}",
        "\\toprule",
        " & \\multicolumn{2}{c}{Control} & \\multicolumn{2}{c}{Treatment} \\\\",
        "\\cmidrule(lr){2-3} \\cmidrule(lr){4-5}",
        "Metric & Mean & Median & Mean & Median \\\\",
        "\\midrule",
        paste(rows, "\\\\"),
        "\\bottomrule",
        "\\end{tabular}"
    )

    dir.create(dirname(file), showWarnings = FALSE, recursive = TRUE)
    writeLines(lines, file)
}
