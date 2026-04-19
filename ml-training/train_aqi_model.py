"""
Training script for aqi_model.tflite (app/src/main/ml/aqi_model.tflite).

After training:
  1. Copy the printed MEAN and STD into AqiPredictor.kt (companion object).
  2. Move aqi_model.tflite into app/src/main/ml/.
  3. Rebuild the app — ML Model Binding regenerates AqiModel.java.

Dataset: concatenated_dataset_Aug_2021_to_July_2024.csv
Target:  EPA AQI (0-500) derived from PM2.5 using official breakpoints.
"""

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
import joblib

# 1. Load data
df = pd.read_csv("concatenated_dataset_Aug_2021_to_July_2024.csv")

# 2. Convert PM2.5 → EPA 0-500 AQI (official breakpoints)
def pm25_to_epa_aqi(c):
    bps = [(0.0,12.0,0,50),(12.1,35.4,51,100),(35.5,55.4,101,150),
           (55.5,150.4,151,200),(150.5,250.4,201,300),(250.5,500.4,301,500)]
    if pd.isna(c): return np.nan
    c = round(c, 1)
    for cl, ch, il, ih in bps:
        if cl <= c <= ch:
            return round((ih - il) / (ch - cl) * (c - cl) + il)
    return 500

df["aqi_epa"] = df["components_pm2_5"].apply(pm25_to_epa_aqi)
df = df.dropna(subset=["aqi_epa","components_pm2_5","components_pm10",
                       "temperature_2m","relative_humidity_2m","wind_speed_10m"])

# 3. Features + target
FEATURES = ["components_pm2_5","components_pm10",
            "temperature_2m","relative_humidity_2m","wind_speed_10m"]
X = df[FEATURES].values.astype("float32")
y = df["aqi_epa"].values.astype("float32")

# 4. Scale inputs — save mean/std for Android
scaler = StandardScaler().fit(X)
X_scaled = scaler.transform(X)

print("MEAN =", scaler.mean_.tolist())   # ← COPY these into Android
print("STD  =", scaler.scale_.tolist())  # ← COPY these into Android

X_tr, X_te, y_tr, y_te = train_test_split(X_scaled, y, test_size=0.2, random_state=42)

# 5. Small MLP (keeps .tflite tiny, ≈20 KB)
model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(5,)),
    tf.keras.layers.Dense(64, activation="relu"),
    tf.keras.layers.Dense(32, activation="relu"),
    tf.keras.layers.Dense(1),
])
model.compile(optimizer="adam", loss="mse", metrics=["mae"])
model.fit(X_tr, y_tr, validation_split=0.1, epochs=40, batch_size=256, verbose=1)

# 6. Evaluate
mse, mae = model.evaluate(X_te, y_te, verbose=0)
print(f"Test MAE: {mae:.2f} AQI units")   # aim for < 25; < 15 is great

# 7. Export .tflite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
with open("aqi_model.tflite", "wb") as f:
    f.write(tflite_model)
