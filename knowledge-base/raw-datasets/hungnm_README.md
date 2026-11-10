---
dataset_info:
  features:
  - name: answer
    dtype: string
  - name: question
    dtype: string
  splits:
  - name: train
    num_bytes: 10672406
    num_examples: 9335
  download_size: 5289086
  dataset_size: 10672406
configs:
- config_name: default
  data_files:
  - split: train
    path: data/train-*
task_categories:
- question-answering
language:
- vi
tags:
- question answering
- medical
- vietnamese
license: apache-2.0
pretty_name: b
size_categories:
- 1K<n<10K
---
## Dataset Summary

Vietnamese-Medical-QA is a question-answering dataset in the healthcare domain, collected from [edoctor](https://edoctor.io/hoi-dap) and [vinmec](https://www.vinmec.com/vi/tin-tuc/hoi-dap-bac-si/).
* Size: After merging data from these two sources, obtained 9335 QA pairs.
* Language: Vietnamese


## Load with Datasets

```python
from datasets import load_dataset

# Load dataset from huggingface
qa_dataset = load_dataset("hungnm/vietnamese-medical-qa")

# print a QA example
print(qa_dataset['train'][0])
```

```json

{
  "question": "Chào bác sĩ,\nRăng cháu hiện tại có mủ ở dưới lợi nhưng khi đau cháu sẽ không ngủ được (quá đau). Tuy nhiên chỉ vài ngày là hết mà thỉnh thoảng nó lại bị đau. Chị cháu bảo là trước chị cháu cũng bị như vậy chỉ là đau răng tuổi dậy thì thôi. Bác sĩ cho cháu hỏi đau răng kèm có mủ dưới lợi là bệnh gì? Cháu có cần đi chữa trị không? Cháu cảm ơn.",
  "answer": "Chào bạn,\nĐể trả lời câu hỏi trên, bác sĩ xin giải đáp như sau:\nRăng bạn hiện tại có mủ dưới lợi gây đau nhức nhiều. Bạn có thể đến phòng khám răng hàm mặt bệnh viện để được thăm khám, chụp phim và tư vấn cho bạn được chính xác\nTrân trọng!"
}

```

## Dataset Version and Update
* Version Details:
    * Current version: 1.0
    * Last Update: 16/02/2024
    * First Release: 16/02/2024

* Update Plan: We will keep updating as we gather additional data.

## Licensing Information
This dataset can be used for any purpose, whether academic or commercial, under the terms of the [Apache 2.0](https://opensource.org/license/apache-2-0) License.


## Citation Information
```bibtex
@misc{vietnamese-medical-qa,
      title={Vietnamese Medical QA: Question Answering dataset for medical in Vietnamese},
      author={Hung Nguyen},
      year={2024},
}
```
