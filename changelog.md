# Changelog - Version 1.1.14

**Release Date**: May 30, 2026  
**Version Code**: 15  
**Version Name**: 1.1.14  

---

### Highlights

* **CRITICAL PRODUCTION BUG FIX — Products Visible Without Login (Seeding Permission Block Bypass)**:
  * Resolved the root cause where guest users were presented with an empty products list on cold installs.
  * In `getProducts()`, fixed a silent bypass bug where unauthenticated guest database-seeding permission failures resolved internally to void without throwing. Added an explicit list check; if Firestore queries or seeding resolve to empty (`0` items), the app automatically falls back to serve the local `INITIAL_PRODUCTS` catalog.

* **REAL-TIME CART MUTATION AUDIT**:
  * Audited and ensured all active cart state mutations execute optimistic React state updates, dynamic items mapping, and background database syncing, resolving all real-time WebView synchronizations.
