# Pattern actions implementation plan

**Goal:** Record short and long Plus Key presses, assign app tiles, and optionally keep reversible actions active until release.

- Extend pattern recognition without changing existing assignment IDs. Test prefix timing, release order, and suppression while recording.
- Persist recorded patterns separately from assignments. Reject duplicate patterns and extensions of reserved held patterns in either edit order.
- Group actions by category and app, discover Quick Settings services, and keep navigation inside the picker. Do not alter Android's tile layout.
- Track hold ownership in the helper. Restore only what PlusKey started, including during shutdown. Do not queue held actions for unlock.
- Build and exercise the new paths on the connected OnePlus; preserve existing assignments and distinguish synthetic input from physical key testing.
