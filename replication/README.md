# CLEM's user study replication package

This folder contains the R script used to conduct the analysis of the CLEM paper. 

## How to install?

You need to have installed the `R language` and ensure `Rscript` is present in your path.  
Launch the `R` REPL by typing `R` from a command line terminal and write:

```R
source("dependencies.R")
```

This will install the project dependencies.

To compute the metrics of the models of new sessions, you also need to have installed `Java` (version 21) and `Maven`, and ensure `mvn` and `java` are present in your path.  
If several versions of `Java` are installed, select the right one before launching the scripts, for instance:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

This step of installing java and maven is not needed if you want to replicate the analysis from the models already present in `data/models/`.

## How to use?

Make sure the `data/` subfolder contains one `.json` file per participation to the study.  
The metrics of the models of each participation are computed in `data/models/`, only for the participations not computed yet.  
Launch the `main.R` script using the following command:

```shell
Rscript main.R
```

The scripts will generate the plots supporting the analysis in the `plots/` subfolder and a table in `tables/`.