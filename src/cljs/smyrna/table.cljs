(ns smyrna.table
  (:require [re-frame.core :as re-frame :refer [reg-event-db reg-sub dispatch subscribe]]
            [reagent.core :as reagent]
            cljsjs.fixed-data-table))

(def Table (reagent/adapt-react-class js/FixedDataTable.Table))
(def Column (reagent/adapt-react-class js/FixedDataTable.Column))
(def Cell (reagent/adapt-react-class js/FixedDataTable.Cell))

(defn default-cell-renderer
  [data row col]
  (nth (nth (:data data) row) col))

(reg-event-db :set-column-width
  (fn [db [_ table column width]]
    (assoc-in db [table :columns column :width] width)))

(defn index-of [s v]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (= v (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn table
  [data-query & {:keys [header-height height cell-renderer header-renderer],
                 :or {header-height 40, cell-renderer default-cell-renderer}}]
  (let [data (subscribe [data-query])
        {:keys [columns column-order shown-columns data] :as orig} @data
        total-width (apply + (map :width (vals columns)))
        total-height (+ 2 (* (inc (count data)) header-height))]
    [Table {:width total-width, :height (or height total-height),
            :rowHeight 40, :rowsCount (count data),
            :isColumnResizing false,
            :onColumnResizeEndCallback #(dispatch [:set-column-width data-query (keyword %2) %1]),
            :headerHeight 40}
     (map (fn [c]
            (let [{:keys [title width]} (columns c)
                  i (index-of column-order c)]
              [Column {:header (if header-renderer
                                 (reagent/as-element [Cell (header-renderer i title)])
                                 title),
                       :columnKey (name c), :key (str (name data-query) "-" (name c)),
                       :width width, :isResizable true,
                       :cell
                       (fn [args]
                         (let [{:strs [columnKey rowIndex]} (js->clj args)]
                           (reagent/as-element [Cell (cell-renderer orig rowIndex i)])))}]))
          shown-columns)]))

(reg-sub :table-width
         #(subscribe [:document-table])
         #(when-let [cols (-> %1 :columns)]
            (->> cols vals (map :width) (apply +))))
