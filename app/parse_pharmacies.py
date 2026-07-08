import sys
import os

input_file = "app/tmp_pharmacies_raw.txt"
output_file = "app/src/main/java/com/example/ui/StaticPharmacies.kt"

if not os.path.exists(input_file):
    print(f"Input file not found: {input_file}")
    sys.exit(1)

entries = []
with open(input_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

header = lines[0].strip().split('\t')
print("Header columns:", header)

for i, line in enumerate(lines[1:], start=1):
    line = line.strip('\r\n')
    if not line:
        continue
    parts = line.split('\t')
    if len(parts) < 5:
        print(f"Skipping line {i}: {line} (too few columns)")
        continue
    
    name = parts[0].strip()
    category_raw = parts[1].strip()
    phone = parts[2].strip()
    address = parts[3].strip()
    lga = parts[4].strip()
    
    # Clean up phone
    if phone.lower() in ["not listed", "no public number on record", "no public number", "none"]:
        phone = "No phone"
    
    # Clean up address
    if address.lower() in ["not specified", "none"]:
        address = lga
        
    # Map category
    category = "Independent Retailer"
    if "medplus" in name.lower():
        category = "Medplus Chain Node"
    elif "healthplus" in name.lower():
        category = "HealthPlus Chain Node"
    elif "wholesale" in category_raw.lower():
        category = "Independent Wholesaler"
    else:
        category = "Independent Retailer"
        
    # Map state
    state = "Lagos"
    lga_lower = lga.lower()
    if any(k in lga_lower for k in ["ota", "sango"]):
        state = "Ogun"
    elif any(k in lga_lower for k in ["port harcourt", "obio-akpor", "rivers"]):
        state = "Rivers"
    elif any(k in lga_lower for k in ["amac", "abuja"]):
        state = "Abuja"
    
    entries.append((name, phone, address, category, state, lga))

print(f"Successfully parsed {len(entries)} entries!")

# Write Kotlin file
kotlin_code = """package com.example.ui

import kotlinx.serialization.Serializable

val staticPharmacies = listOf(
"""

for entry in entries:
    name, phone, address, cat, state, lga = entry
    # Escape double quotes in strings
    name_esc = name.replace('"', '\\"')
    phone_esc = phone.replace('"', '\\"')
    address_esc = address.replace('"', '\\"')
    cat_esc = cat.replace('"', '\\"')
    state_esc = state.replace('"', '\\"')
    lga_esc = lga.replace('"', '\\"')
    
    kotlin_code += f'    VerifiedPharmacy("{name_esc}", "{phone_esc}", "{address_esc}", "{cat_esc}", "{state_esc}", "{lga_esc}"),\n'

kotlin_code += ")\n"

with open(output_file, 'w', encoding='utf-8') as f:
    f.write(kotlin_code)

print(f"Successfully wrote {output_file}!")
