"""Train an XGBoost classifier with hyperparameter tuning and export it to PMML format."""

import pandas as pd
from xgboost import XGBClassifier
from sklearn.metrics import classification_report
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.model_selection import GridSearchCV
from nyoka import xgboost_to_pmml

DATA_DIR = "./data"
MODEL_DIR = "./model_training"

# Load data
X_train = pd.read_csv(f"{DATA_DIR}/X_train.csv", index_col="idx")
X_test = pd.read_csv(f"{DATA_DIR}/X_test.csv", index_col="idx")
y_train = pd.read_csv(f"{DATA_DIR}/y_train.csv", index_col="idx")["label"]
y_test = pd.read_csv(f"{DATA_DIR}/y_test.csv", index_col="idx")["label"]

# Pipeline
pipeline = Pipeline(
    [
        ("scaler", StandardScaler()),
        (
            "xgb",
            XGBClassifier(eval_metric="logloss", random_state=42),
        ),
    ]
)

# Hyperparameter grid
param_grid = {
    "xgb__n_estimators": [100, 200],
    "xgb__max_depth": [4, 6],
    "xgb__learning_rate": [0.05, 0.1],
    "xgb__subsample": [0.8],
    "xgb__colsample_bytree": [0.8],
}

# Grid search (3-fold CV)
grid = GridSearchCV(
    pipeline, param_grid, cv=3, scoring="accuracy", n_jobs=-1, verbose=1
)
grid.fit(X_train, y_train)

# Best model evaluation
best_model = grid.best_estimator_
y_pred = best_model.predict(X_test)
report_txt = classification_report(y_test, y_pred)

# Save report
with open(f"{MODEL_DIR}/classification_report_xgboost.txt", "w", encoding="utf-8") as f:
    f.write(f"Best params: {grid.best_params_}\n\n")
    f.write(report_txt)

# Export to PMML
feature_names = X_train.columns.tolist()
xgboost_to_pmml(
    pipeline=best_model,
    col_names=feature_names,
    target_name="label",
    pmml_f_name=f"{MODEL_DIR}/xgboost_model.pmml",
)
