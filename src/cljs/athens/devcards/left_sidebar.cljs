(ns athens.devcards.left-sidebar
  (:require
    [athens.db :as db]
    ["/components/Button/Button" :refer [Button]]
    [athens.views.left-sidebar :refer [left-sidebar]]
    [devcards.core :refer [defcard-rg]]
    [posh.reagent :refer [transact!]]))


(defcard-rg Create-Shortcut
  [:> Button {:on-click (fn []
                       (let [n (:max-eid @db/dsdb)]
                         (transact! db/dsdb [{:page/sidebar n
                                              :node/title   (str "Page " n)
                                              :block/uid    (str "uid" n)}])))} "Create Shortcut"])


(defcard-rg Left-Sidebar
  [:div {:style {:display "flex" :height "60vh"}}
   [left-sidebar]]
  {:padding false})
