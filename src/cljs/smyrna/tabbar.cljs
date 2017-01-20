(ns smyrna.tabbar
  (:require [re-frame.core :refer [dispatch subscribe]]
            [smyrna.utils :refer [setter-event]]))

(defn tabbar [query labels-and-components &
              {:keys [disabled-item?],
               :or {disabled-item? (constantly false)}}]
  (let [tab (subscribe [query])
        setter (setter-event query)
        pairs (partition 2 labels-and-components)
        labels (map first pairs)
        components (map second pairs)]
    [:table {:class "fixed-top tabbar"}
     [:tbody
      [:tr
       [:td
        [:ul {:class "nav"}
         (doall
          (for [[n label] (map-indexed vector labels)]
            (condp = label
              :glue [:li {:key (str "navbar-" (name query) "-" n),
                          :class "glue"}]
              :inline [:li {:key (str "navbar-" (name query) "-" n),
                            :class "inline-comp"} (nth components n)]
              (let [component (nth components n)
                    disabled? (disabled-item? n)]
                [:li {:key (str "navbar-" (name query) "-" n),
                      :class (str "navbar-" (name query) "-" n " " (if (= n @tab) "item selected" "item"))}
                 [:a {:href "#"
                      :class (if disabled? "disabled" "")
                      :on-click #(cond
                                   disabled? nil
                                   (vector? component) (dispatch [setter n])
                                   :otherwise (component))} label]]))))]
        ]]
      [:tr
       [:td
        [:div {:class "tab"}
         (nth components @tab)]]]]]))
