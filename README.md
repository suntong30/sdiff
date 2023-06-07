# Understanding Differencing Algorithms for Mobile Application Updates
In this repository, we release the dataset and tools in our measurement paper. We also open-source our novel algorithm, **sdiff**.

To help you quickly navigate and have the ability to understand the different pieces, we have created different folders for different experiments. There are README files within each folder that provide instructions on validating the experiment-specific artifacts. At the very top of the README instructions, we also specify which results/plots (e.g. Figure 2 in the paper) the folder is responsible. Lastly, to make it easy here are some generic principles we followed for releasing the artifacts:

#### Dataset
- If the dataset is small enough, we included the dataset file in this repository itself.
- If the dataset files are huge, we use a small sample of the dataset in the repository to demonstrate the functionality/correctness. You can replace the small subset with the full dataset (provided using a link to a Shared Google Drive folder) to further validate. In either case, we provide full processed results as well. 

#### Data Analysis, Model/Plot Generation
- If data analysis is involved, our instructions will contain information on how to process the data.
- No matter what the dataset size is, we provide the fully generated results and/or plots. If you decide to run the analysis and/or plotting scripts, the outcome of processing will simply replace the existing files in the repository.
---
### Prerequisite
1. On the server side, our implementation relies on the `jna-5.12.1.jar`, which is based on `x86`. If you want to running on the `ARM` server, you need to download the corresponding `jna` tool on https://github.com/java-native-access/jna and replace it. 
2. We use `memory-profiler` tool to measure memory usage.(https://pypi.org/project/memory-profiler/). You can use the following command to install it.
> $ pip install -U memory_profiler
---
### Paper Structure to Folder Structure


