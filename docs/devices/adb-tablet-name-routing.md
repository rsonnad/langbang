# ADB Tablet Name Routing

Use the Android device name as the first identity check when both LangBangML
tablets are connected. Model names and IP routes can overlap or drift; the
renamed device names are clearer:

- `A9 Cream Tab` -> `cream`
- `A11 Blue Tab` -> `blue`

## Identify Connected Tablets

```bash
scripts/identify-adb-tablets.sh
```

The helper prints one row per connected ADB route:

```text
alias   adb_serial                  device_name     model     android_serial
cream   100.103.110.7:5555          A9 Cream Tab    SM-X210   R95Y301R05A
blue    192.168.1.123:5555          A11 Blue Tab    SM-X238U  R5GL20C46YJ
```

Duplicate rows for one physical tablet are normal when ADB exposes both a
network route and an mDNS route. Compare `android_serial` to confirm duplicates.

## Get The Current ADB Serial

```bash
scripts/identify-adb-tablets.sh --serial cream
scripts/identify-adb-tablets.sh --serial blue
```

Use the returned serial directly:

```bash
ADB_SERIAL="$(scripts/identify-adb-tablets.sh --serial cream)"
adb -s "$ADB_SERIAL" shell settings get global device_name
```

## Screenshot Pulls

`scripts/pull-tablet-screenshots.sh` now defaults to the cream tablet by name.
Use `TABLET_ALIAS=blue` when pulling screenshots from the blue tablet:

```bash
scripts/pull-tablet-screenshots.sh
TABLET_ALIAS=blue scripts/pull-tablet-screenshots.sh
```

If a tablet has no `Cream Tab` or `Blue Tab` string in `settings get global
device_name`, do not infer from model alone. Rename the tablet first or pass an
explicit `ADB_SERIAL`.
