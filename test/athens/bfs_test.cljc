(ns athens.bfs-test
  (:require
    [athens.common-db :as common-db]
    [athens.common-events.bfs :as bfs]
    [clojure.test :refer [deftest is] :as t]
    [datascript.core :as d]))


(def tree-with-pages
  [{:node/title     "Welcome"
    :page/sidebar   0
    :block/children [#:block{:uid      "block-1"
                             :string   "block with link to [[Welcome]]"
                             :open     false
                             :children []}]}])


(def tree-without-page
  [{:block/uid "eaa4c9435",
    :block/string "block 1",
    :block/open true,
    :block/children
    [{:block/uid "88c9ff662",
      :block/string "B1 C1",
      :block/open true}
     {:block/uid "7d11d532f",
      :block/string "B1 C2",
      :block/open true,
      :block/children
      [{:block/uid "db5fa9a43",
        :block/string "B1 C2 C1",
        :block/open true}]}]}])


(deftest get-individual-blocks-from-tree-test
  (let [db (d/empty-db common-db/schema)]
    (is (= [#:op{:type :page/new, :atomic? true, :args {:name "Welcome"}}
            #:op{:type :block/new, :atomic? true, :args {:block-uid "block-1", :position {:ref-name "Welcome", :relation :last}}}
            #:op{:type :composite/consequence,
                 :atomic? false,
                 :trigger #:op{:type :block/save},
                 :consequences
                 [#:op{:type :page/new, :atomic? true, :args {:name "Welcome"}}
                  #:op{:type :block/save, :atomic? true, :args {:block-uid "block-1", :string "block with link to [[Welcome]]"}}]}]
           (bfs/internal-representation->atomic-ops db tree-with-pages nil)))

    (is (= [#:op{:type :block/new, :atomic? true, :args {:block-uid "eaa4c9435", :position {:ref-name "title", :relation :first}}}
            #:op{:type :block/save, :atomic? true, :args {:block-uid "eaa4c9435", :string "block 1"}}
            #:op{:type :block/new, :atomic? true, :args {:block-uid "88c9ff662", :position {:ref-uid "eaa4c9435", :relation :last}}}
            #:op{:type :block/save, :atomic? true, :args {:block-uid "88c9ff662", :string "B1 C1"}}
            #:op{:type :block/new, :atomic? true, :args {:block-uid "7d11d532f", :position {:ref-uid "88c9ff662", :relation :after}}}
            #:op{:type :block/save, :atomic? true, :args {:block-uid "7d11d532f", :string "B1 C2"}}
            #:op{:type :block/new, :atomic? true, :args {:block-uid "db5fa9a43", :position {:ref-uid "7d11d532f", :relation :last}}}
            #:op{:type :block/save, :atomic? true, :args {:block-uid "db5fa9a43", :string "B1 C2 C1"}}]
           (bfs/internal-representation->atomic-ops db tree-without-page {:ref-name "title" :relation :first})))))


(comment
  (binding [t/*stack-trace-depth* 5] (t/run-tests))

  (bfs/internal-representation->atomic-ops (d/empty-db common-db/schema) tree-without-page {:ref-name "title" :relation :first}))