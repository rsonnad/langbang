#!/usr/bin/env bash
# Populates a new LangBang phrase group with the Protopia / Wychylone sentences.
# Usage: TOKEN=xxxxx ./populate_protopia_group.sh
set -euo pipefail

API="https://langbang.org/v1/agent/phrases"
TITLE="Protopia – Wychylone"
: "${TOKEN:?Set TOKEN env var to your LangBang agent token (Android Settings)}"

sentences=(
"Kiedy pisałam pierwsze wydanie Wychylonych w przeszłość, czułam, że coś się zmienia – choć jeszcze nie potrafiłam tego nazwać, dopiero uczyłam się o tym opowiadać."
"Wiedziałam, że dotychczasowe modele rozwoju się wyczerpały, a napięcia klimatyczne, społeczne i gospodarcze narastają."
"Czułam, że potrzebujemy nowego języka, by opisać świat, który chce się wyłonić."
"Tak pojawiło się pojęcie protopii – jako pierwszego, realnego kroku w stronę lepszego świata."
"Przyszłości bliskiej, rozpiętej między tym, co znane, a utopią, która zawsze pozostaje poza horyzontem."
"Dziś jeszcze mocniej niż wtedy wiem, że działanie jest nieodłączną częścią myślenia, a aktywna nadzieja – umiejętnością, którą warto pielęgnować, jeśli chcemy realnie przekształcać rzeczywistość."
"W ciągu tego czasu protopia zaczęła żyć własnym życiem – weszła do szerszego obiegu, zagościła w rozmowach, konceptach, marzeniach."
"Trafiła też na moją wizytówkę – dziś pełnię rolę „dyrektorki ds. protopii”…"
)

echo "== status =="
curl -sS "https://langbang.org/v1/agent/status" -H "Authorization: Bearer $TOKEN"; echo

i=0
for s in "${sentences[@]}"; do
  i=$((i+1))
  echo "== POST $i/${#sentences[@]} =="
  jq -n --arg t "$TITLE" --arg p "$s" \
    '{groupTitle:$t, atomic:true, phrase:{polish:$p}}' \
  | curl -sS -X POST "$API" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      --data-binary @- ; echo
done

echo "== verify =="
curl -sS "$API?groupTitle=$(jq -rn --arg t "$TITLE" '$t|@uri')" \
  -H "Authorization: Bearer $TOKEN"; echo
