# msusel-pique-vendor
msusel-pique-vendor is a project developed as a National Science Foundation Research Experience for Undergraduates at Montana State University in the summer of 2021. msusel-pique-vendor is an extension of msusel-pique. It allows users to derive quality assessment models and perform security quality assessments for projects written in the C programming language. The security analysis is performed using the flawfinder and cppcheck static analyzers for the C language.

## Introduction: msusel-pique
msusel-pique is a fork of the QATCH project found from QuthEceSoftEng's [GitHub](https://github.com/AuthEceSoftEng/qatch) and [Website](http://softeng.issel.ee.auth.gr/).  

This fork modifies QATCH to behave more like a library by modularizing code, introducing maven project structure, removing GUI elements, removing main methods, and having all methods be language and tool agnostic.

Due to the major changes of intent and design, the project was renamed to PIQUE: a *Platform for Investigative software Quality Understanding and Evaluation*.
QATCH legacy build, config, rulesets, and default model files are left in an archive folder.

PIQUE is a collection of library functions and runner entry points designed to support experimental software quality analysis from a language-agnostic perspective.
To remain language-agnostic, this project provides the abstractions, interfaces, and algorithms necessary for quality assessment, but leaves the task of defining language-specific static analysis operations to dependent language-specific projects that will use MSUSEL-PIQUE as a dependency.
To facilitate newcomers, this platform provides default classes for each quality assessment component to allow the platform to be used "out of the box", and for those familiar with quality assessment approaches, the platform allows each component to be overridden with experimental approaches.


PIQUE is not yet added to the Maven central repository, so this project will need to be [built](#building) and installed (via Maven) before it can be used as a library. 
___

## Components: msusel-pique
PIQUE provides five components that work together to achieve quality assessment: *Runner, Analysis, Calibration, Model*, and *Evaluation*.
Language specific extensions of PIQUE fulfill the interface contracts required by these components to achieve language-specific assessment without needing to invest major time into constructing a quality assessment engine.
- **Runner**: The *Runner* component utilizes the constructs of PIQUE to automate the two components necessary for quality assessment: (1) Deriving a quality model, and (2) Using that model to assess quality of a system.
- **Analysis**: The *Analysis* component provides the abstractions necessary for a language-specific PIQUE extension to instance its static analysis tools as PIQUE domain objects.
- **Calibration**: The *Calibration* component provides the abstractions necessary for two of the three components of quality model experimental design: (1) Edge weighting, and (2) Benchmarking.  Additionally, a default edge weighting concrete class and a default benchmarking concrete class are provided.
- **Model**: PIQUE assumes quality assessment will use a tree structure as its evaluation model.  The *Model* component provides the abstract objects necessary for such an assessment.  The model is instantiated via a *.json* quality model configuration file. A generic example of a model can be found in `src/test/resources/quality_models` and numerous concrete quality models can be found in the [msusel-pique-csharp](https://github.com/msusel-pique/msusel-pique-csharp) project example.
- **Evaluation**: The *Evaluation* component provides the third component necessary for quality model experimental design: (3) the algorithms and strategies used for model evaluation aggregation; specifically, normalization, aggregation, and utility functions. Additionally, this component provides default concrete evaluators for each model node type, a default normalizer, and a default utility function. 


## Components: msusel-pique-vendor
piqueVendor.runnable extends the basic functionalities of the pique runnable component. Changes were made in the QualityModelDeriver and ProjectsEvaluator classes. 

The tool component in pique vendor is an extension of the analysis component in pique. The classes FlawfinderToolWrapper and CppCheckToolWrapper are implementations of the pique ITool interface.

The calibration component on pique-vendor has classes BinaryBenchmarker and BinaryWeighter which implement the pique IBenchmarker and IWeighter interfaces, respectively.

The pique-vendor evaluator component has three classes that extend the pique Evaluator class and it has one class that extends the pique DefaultUtility class. 

# Static Analysis Tools
The following static analyzer will need to be installed:
flawfinder - https://github.com/david-a-wheeler/flawfinder
cppcheck - http://cppcheck.sourceforge.net/

___

## Build Environment
- Java 8+
- Maven
___
## Building
1. Ensure the [Build Environment](#build-environment) requirements are met.
1. Clone repository into `<project_root>` folder
1. Run `mvn test` from `<project_root>`. Fix test errors if needed. Errors, if they occur, will likely be from misconfiguration of R and the jsonlite library.
1. Run `mvn install` from `<project_root>`. 
msusel-pique is now available as a dependency to extend in a personal project. 
*(Eventually `mvn deploy` will be used instead.)*

___
## Running
After installing the static analysis tools, got to the resources directory and find the piqueVendor.properties file. Write the path to each tool in the local host as the values of tool.filepath and tool2.filepath.
Any project that is to be assessed should be added to the projectsToAsses directory. 
Run ProjectsEvaluator and the output will go to the out directory.

To derive a new model: 
write the description of the model and add it to the resources directory and write its name as a new value for blankqm.filepath in the piqueVendor.properties file. Subjective comparison matrices for this model should be added to the comparisonMatrices directory.

The benchmark directory contains the base files against whose values the projects that are to be assessed are measured. The contents of this directory should be adjusted before deriving a model, if so desired.
