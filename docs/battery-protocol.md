# Battery protocol (JBD BMS over BLE)

Reverse-engineered for an **Eco-Worthy LiFePO4 100 Ah** (device name
`DP04S007L4S100A`, `L4S` = LiFePO4 4S = 12.8 V). It speaks the common
**JBD / Jiabaida** BMS protocol.

## GATT
| UUID | Props | Role |
|------|-------|------|
| Service `0xFF00` | | BMS service |
| Char `0xFF01` | notify | responses (data arrives here) |
| Char `0xFF02` | write-no-response | commands go here |

MTU is 23, so responses are fragmented into 20-byte chunks and must be
reassembled into a frame (start `0xDD` … end `0x77`).

## Frames
**Read command:** `DD A5 <reg> 00 <cksum_hi> <cksum_lo> 77`
with `cksum = (0x10000 − (reg + len)) & 0xFFFF` (len = 0 here).

**Response:** `DD <reg> <status> <len> <len bytes payload> <cksum_hi> <cksum_lo> 77`
(status 0 = OK).

Verified commands:
- Basic info: `DD A5 03 00 FF FD 77`
- Cell voltages: `DD A5 04 00 FF FC 77`
- Name: `DD A5 05 00 FF FB 77`

## Register 0x03 – basic info (payload offsets)
| Off | Bytes | Field | Unit |
|----:|:-----:|-------|------|
| 0 | 2 | pack voltage | 10 mV |
| 2 | 2 | current (signed) | 10 mA |
| 4 | 2 | remaining capacity | 10 mAh |
| 6 | 2 | nominal capacity | 10 mAh |
| 8 | 2 | cycles | – |
| 16 | 2 | protection status | bitmask |
| 19 | 1 | **RSOC (state of charge)** | % |
| 20 | 1 | FET status | bit0 charge, bit1 discharge |
| 21 | 1 | cell count | – |
| 22 | 1 | NTC count | – |
| 23 | 2×N | temperature(s) | 0.1 K → °C = v·0.1 − 273.15 |

## Register 0x04 – cell voltages
`len` bytes = cellCount × 2, each cell in mV (big-endian).

## Notes
- Current sign (charge vs. discharge) is firmware-dependent — verify in the field
  with the motor running. The app treats negative current as discharge.
- Range/time estimate: `remainingAh ÷ dischargeA` → hours; `× speed` → distance.
