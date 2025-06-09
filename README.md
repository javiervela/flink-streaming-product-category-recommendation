# Flink Streaming Product Category Recommendation

A real-time decision pipeline using Apache Flink for personalized product category display based on historical purchases. Includes model training, PMML deployment, and evaluation via A/B testing.

---

## Project Overview

This project is designed to support targeted product category recommendations in an e-commerce setting. It uses customer demographic and purchase history data to predict whether a specific user should be shown a new product category on the website.

The backend pipeline is built using Apache Flink for real-time streaming decision-making, and the model is trained offline and exported in PMML format for easy deployment into Flink.

---

## Dataset

The data is organized under the `data/` directory:

```
data/
├── X_train.csv     # Features for training (indexed by customer ID)
├── X_test.csv      # Features for testing
├── y_train.csv     # Labels for training (binary: show category or not)
└── y_test.csv      # Labels for testing
```

Each row corresponds to a customer session with input features like:

- Age
- Gender indicators (`man`, `woman`)
- Purchase counts across many product categories (e.g. `cat17`, `cat42`, etc.)

---

## Model Training

The training code lives in the `model_training/` directory:

```
model_training/
├── train.py                               # XGBoost training and PMML export
├── classification_report_xgboost.txt      # Evaluation report for XGBoost
├── classification_report_randomforest.txt # Optional RandomForest report
└── xgboost_model.pmml                     # Exported model for Flink
```

### Training Methodology

1. Preprocessing is done using `StandardScaler`.
2. The model is trained using `XGBoostClassifier` with `GridSearchCV` to optimize key hyperparameters.
3. The best model is evaluated using accuracy and confusion matrix.
4. The model is exported to PMML format using Nyoka for deployment into Flink.

---

## Setup and Execution

### 1. Install dependencies (Poetry required)

```bash
poetry install
```

This installs Python dependencies listed in `pyproject.toml`, including:

- `xgboost`
- `scikit-learn`
- `nyoka`
- `pandas`

### 2. Activate virtual environment

```bash
poetry shell
```

### 3. Run the training script

```bash
python model_training/train.py
```

This will:

- Train the model
- Print best hyperparameters
- Save evaluation report
- Export the model to `xgboost_model.pmml`

---

## Directory Structure

```
.
├── data/                          # Training/testing data
├── model_training/               # Model training and export scripts
├── pyproject.toml                # Poetry config
├── poetry.lock                   # Locked dependencies
└── README.md                     # This file
```

---

## Next Steps

Once the PMML model is generated, it can be integrated into the Flink stream processing pipeline to make real-time decisions for each incoming user session based on data streamed from Kafka.

The Flink pipeline code and deployment instructions will follow in the next steps of the project.

---

## Evaluation

After training, evaluation metrics are saved in `classification_report_xgboost.txt`. This can be used to:

- Validate model performance
- Compare with alternative models like RandomForest (see `classification_report_randomforest.txt`)
