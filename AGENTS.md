# AGENT RULES — OFFLINE SATNAV SYSTEM

You are building a production-grade offline GPS navigation system.

You must follow strict engineering discipline.

---

## CORE PRINCIPLES

1. OFFLINE FIRST

* No internet dependency anywhere
* All maps, routing, terrain must load locally

2. PERFORMANCE CRITICAL

* App must handle 5–10GB datasets
* Avoid loading large files into memory
* Use streaming / tile-based loading

3. MODULAR DESIGN

* Keep modules isolated:

  * map
  * routing
  * navigation
  * location

4. DO NOT OVERENGINEER

* Implement only what is required for current phase

---

## MAP RULES

* Always use MBTiles (never raw tiles)
* Do not load entire map into memory
* Use tile-based rendering

---

## ROUTING RULES

* Use GraphHopper only
* Load graph-cache from local storage
* Never recompute graph on device

---

## TERRAIN RULES

* Use raster-dem tiles
* Use hillshade for visualization
* Do not load full DEM into memory

---

## NAVIGATION RULES

* Always map-match GPS to road
* Detect deviation and reroute
* Keep navigation loop lightweight

---

## THREADING RULES

* Routing → background thread
* UI → main thread
* Never block UI thread

---

## DATA STORAGE RULES

* Store large files in external storage
* Use lazy loading
* Cache frequently used data

---

## CODE QUALITY

* Use Kotlin best practices
* Follow MVVM strictly
* Keep code readable and modular

---

## FAILURE HANDLING

If something fails:

1. Debug root cause
2. Fix cleanly
3. Do not hack temporary fixes

---

## EXECUTION RULE

Only work on ONE phase at a time.

Do NOT jump ahead.
