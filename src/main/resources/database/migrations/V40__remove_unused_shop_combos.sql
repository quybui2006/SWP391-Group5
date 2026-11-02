-- Remove combo tables after the application no longer uses shop combos.
DROP TABLE IF EXISTS cart_combo_items;
DROP TABLE IF EXISTS shop_combo_items;
DROP TABLE IF EXISTS shop_combos;
