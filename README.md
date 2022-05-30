# MSUSEL-PIQUE-VENDOR
## Introduction
This project is an operationalized PIQUE model for the assessment of vendor/contractor projects written in C.

# Introduction: security quality assessments
The goal of this project is to produce quality models that can be used to perform security quality assessments for projects written in the C programming language, as would be seen in many vendor software releases. A quality model is a definition of different software characteristics that may be used to describe its quality. This definition is given to the PIQUE platform, along with the path to static analysis tools, and a repository of C language projects that are to be used for benchmarking. A model derivation process is then run in PIQUE, and this outputs a quality model that is ready to perform security assessments for C language projects.

MSUSEL-PIQUE-VENDOR is an extension of MSUSEL-PIQUE. It extends the PIQUE libraries to create a working system for C security quality investigation. The security analysis is performed using the Flawfinder and Cppcheck static analyzers for the C language.

## MSUSEL-PIQUE: origin and structure
MSUSEL-PIQUE is a fork of the QATCH project found from QuthEceSoftEng's [GitHub](https://github.com/AuthEceSoftEng/qatch) and [Website](http://softeng.issel.ee.auth.gr/).  

This fork modifies QATCH to behave more like a library by modularizing code, introducing maven project structure, removing GUI elements, removing main methods, and having all methods be language and tool agnostic.

Due to the major changes of intent and design, the project was renamed to PIQUE: a *Platform for Investigative software Quality Understanding and Evaluation*.

PIQUE is not yet added to the Maven central repository, so this project will need to be [built](#building) and installed (via Maven) before it can be used as a library. 
___

## Run Environment
- Windows 10+ (Linux + Mac coming soon with docker packaging!)
- Java 8+ (not tested for Java 11+)
- Python 3.6+

## Running
With release v1.0, we have packaged PIQUE-vendor's required files into a zipped folder named `pique-vendor-package`, included as a tagged release 1.0. This zipped folder includes the following folders/files:
- benchmark -- holds 42 C source files that were used to calibrate the PIQUE model
- comparisonMatrices -- holds pairwise comparisons of quality aspects and factors in the model
- projectToAssess -- Place the files you want analyzed in this folder
- tools -- flawfinder and cppcheck and their respective environments
- modelDescription.json -- a description of the PIQUE model
- msusel-pique-vendor-1.0.0-jar-with-dependencies.jar -- PIQUE-vendor runnable
- piqueVendor.properties -- properties file **to be filled out** before running the runnable


### To Run
1. Configure the piqueVendor.properties file with your environment's values.
   1. (optional) add C source files to the benchmark directory to provide better model accuracy for your context
2. Run the model deriver with `java -jar msusel-pique-vendor-1.0.0-jar-with-dependencies.jar -d piqueVendor.properties`. This generates an output directory (as specified in the properties file) along with the derived model (CVendorQualityModel.json) and individual tool results for each project in the benchmark directory if specified in the properties.
3. Run the model evaluator with `java -jar msusel-pique-vendor-1.0.0-jar-with-dependencies.jar -e piqueVendor.properties`. This generates a json file (<file-name>_evalResults.json) in the output directory named after the project(s) that was analyzed. 


## For Developers
If you intend to develop on PIQUE-vendor, these instructions will get you set up:

## Build Environment
- Java 8+ (not tested for Java 11+)
- Maven 3.6.3+
- Python 3.6+
- [PIQUE 0.9.3+](https://github.com/MSUSEL/msusel-pique)
## Components: msusel-pique-vendor

# Static Analysis Tools
The following static analyzer will need to be installed (NOTE: the packaged zip included in release v1 contains these tools already)
[Flawfinder](https://github.com/david-a-wheeler/flawfinder )
[Cppcheck](http://cppcheck.sourceforge.net/)

## Building
1. Ensure the [Build Environment](#build-environment) requirements are met including having already built and installed [PIQUE](https://github.com/MSUSEL/msusel-pique) using `mvn install`.
2. Clone repository into `<project_root>` folder.
3. Derive the model as defined in the [Model Derivation](#model-derivation) section below
4. Assess a binary as defined in the [Model Evaluation](#model-evaluataion) section below

### Model Derivation
First the model's properties must be configured in the `piqueVendor.properties` file. Then, the model must be derived using a benchmark repository. This is done by running the `src/main/java/piquebinaries/runnable/QualityModelDeriver.java` file.

### Model Evaluataion
Finally, the `src/main/java/piquebinaries/runnable/SingleProjectEvaluator.java` file may be run to analyze a c source file. This will produce output in the outputPath folder specified in the poperties file.

### Packaging
Package into a jar file with `mvn package`

___

## References
Rice, David. 2020. An Extensible, Hierarchical Architecture for Analysis of Software Quality Assurance. [Master’s thesis, Montana State University]. <br/>
M. Siavvas, K. Chatzidimitriou, and A. Symeonidis. Qatch - an adaptive framework for software product quality assessment. Expert Systems With Applications, 86:350–366, 2017.<br/>
S. Wagner, K. Lochmann, S. Winter, F. Deissenboeck, E. Juergens, M. Herrmannsdoerfer, L. Heinemann, M. Kläs, J. Heidrich, R. Ploesch, A. Göeb, and C. Koerner. The quamoco quality meta-model, technical report. Technical Report TUM-I1281, Technische Universität, München, 2012.

