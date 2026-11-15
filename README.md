# PGRF1 – Úloha 2: Vyplnění n-úhelníku, ořezání úsečky a polygonu

## Spuštění
- JDK 27 (projekt v IntelliJ IDEA, zdrojová složka `src`)
- Spouštěcí třída: `src/Main.java`

## Ovládání
| Akce | Ovládání |
|---|---|
| Kreslení hrany polygonu | Tažení levým tlačítkem myši (režim Polygon) |
| Kreslení samostatné úsečky | Tažení levým tlačítkem myši (režim Úsečky) |
| Zarovnaná úsečka (vodorovná / svislá / úhlopříčná) | Držet Shift během tažení |
| Přepnutí režimu Polygon / Úsečky | Tlačítko „Režim“ |
| Seed fill | Tlačítko „Seed fill“ + kliknutí do oblasti |
| Hraniční podmínka seed fillu | Tlačítko „Omezení: pozadí / hranice“ |
| Posun vrcholu ořezávacího polygonu | Tažení pravým tlačítkem myši (režim Polygon) |
| Změna velikosti ořezávacího okna | Tažení rohu pravým tlačítkem myši (režim Úsečky) |
| Orientace ořezávacího polygonu | Tlačítko „Orientace: CW / CCW“ |
| Vymazání plátna a reset všech stavů | Klávesa C nebo tlačítko „Vymazat“ |

## Implementované algoritmy
- **Scan-line** – `graphics/filler/ScanlineFiller` (vlastní řazení průsečíků insertion sortem)
- **Seed fill** – `graphics/filler/QueueSeedFiller` (fronta místo rekurze, 4-okolí),
  hraniční podmínky v `graphics/filler/FillCondition` (barva pozadí / barva hranice)
- **Sutherland-Hodgman** – `graphics/clipping/SutherlandHodgmanClipper`
  (ořezávací polygon v `model/ClippingPolygon`, 5 vrcholů, vždy konvexní)
- **Cohen-Sutherland** – `graphics/clipping/CohenSutherlandClipper`
  (ořezávací okno v `model/ClipWindow`)
- **Triviální algoritmus úsečky** – `graphics/rasterizer/TrivialLineRasterizer`
  (neantialiasovaná hranice pro seed fill)

## Poznámky k implementaci
- Při seed fillu se uživatelem zadaná hranice vykresluje plnou bílou barvou bez antialiasingu.
  Antialiasované pixely jsou směsí barev, takže podmínka "barva hranice" by jimi protekla.
- Ořezávací polygon nelze editací změnit na nekonvexní, takový posun vrcholu je odmítnut.
- Orientace se určuje znaménkem plochy (shoelace). Osa Y na obrazovce směřuje dolů,
  proto kladná plocha odpovídá vizuálně orientaci po směru hodinových ručiček.
- Pokud se při ořezání nekonvexního polygonu výsledek rozpadne na více částí, Sutherland-Hodgman
  vrátí jeden polygon, jehož části jsou spojeny hranami po okraji ořezávacího polygonu
  (vlastnost algoritmu).
- Spustitelné testy ořezávání: `checks/Check.java`.
