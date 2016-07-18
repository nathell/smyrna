(ns smyrna.table
  (:require [re-frame.core :as re-frame :refer [register-handler dispatch subscribe]]
            [reagent.core :as reagent]
            cljsjs.fixed-data-table))

(def Table (reagent/adapt-react-class js/FixedDataTable.Table))
(def Column (reagent/adapt-react-class js/FixedDataTable.Column))
(def Cell (reagent/adapt-react-class js/FixedDataTable.Cell))

(defn default-cell-renderer
  [row i]
  (nth row i))

(register-handler :set-column-width
  (fn [state [_ table column width]]
    (assoc-in state [table :columns column :width] width)))

(defn table
  [data-query & {:keys [header-height height cell-renderer],
                 :or {header-height 40, cell-renderer default-cell-renderer}}]
  (let [data (subscribe [data-query])]
    (fn render-home-page []
      (let [{:keys [columns shown-columns data]} @data
            total-width (apply + (map :width (vals columns)))
            total-height (+ 2 (* (inc (count data)) header-height))]
        [Table {:width total-width, :height (or height total-height),
                :rowHeight 40, :rowsCount (count data),
                :isColumnResizing false,
                :onColumnResizeEndCallback #(dispatch [:set-column-width data-query (keyword %2) %1]),
                :headerHeight 40}
         (map-indexed (fn [i c]
                        (let [{:keys [title width]} (columns c)]
                          [Column {:header title, :columnKey (name c), :key (str (name data-query) "-" (name c)),
                                   :width width, :isResizable true,
                                   :cell
                                   (fn [args]
                                     (let [{:strs [columnKey rowIndex]} (js->clj args)]
                                       (reagent/as-element [Cell (cell-renderer (nth data rowIndex) i)])))}]))
                      shown-columns)]))))
