(ns athens.types.tasks.view
  "Views for Athens Tasks"
  (:require
    ["/components/Block/BlockFormInput"         :refer []]
    ["/components/Block/Taskbox"                :refer [Taskbox]]
    ["/components/Icons/Icons"                  :refer [PencilIcon]]
    ["/components/ModalInput/ModalInput"        :refer [ModalInput]]
    ["/components/ModalInput/ModalInputPopover" :refer [ModalInputPopover]]
    ["/components/ModalInput/ModalInputTrigger" :refer [ModalInputTrigger]]
    ["@chakra-ui/react"                         :refer [FormControl
                                                        FormLabel
                                                        Text
                                                        Flex
                                                        AvatarGroup
                                                        Avatar
                                                        Box
                                                        Divider
                                                        Button
                                                        Badge
                                                        Select
                                                        HStack]]
    [athens.common-db                           :as common-db]
    [athens.common-events.graph.ops             :as graph-ops]
    [athens.dates                               :as dates]
    [athens.db                                  :as db]
    [athens.reactive                            :as reactive]
    [athens.types.core                          :as types]
    [athens.types.dispatcher                    :as dispatcher]
    [athens.types.tasks.events :as events]
    [athens.types.tasks.generic-textarea :as generic-textarea]
    [athens.types.tasks.inline-task-title :as inline-task-title]
    [athens.types.tasks.shared :as shared]
    [re-frame.core                              :as rf]
    [tick.core                                  :as t]))


;; View



(defn task-priority-view
  [parent-block-uid priority-block-uid]
  (let [priority-id        (str (random-uuid))
        priority-block     (reactive/get-reactive-block-document [:block/uid priority-block-uid])
        allowed-priorities (shared/find-allowed-priorities)
        priority-string    (:block/string priority-block "(())")
        priority-uid       (subs priority-string 2 (- (count priority-string) 2))]
    [:> FormControl {:display "contents"}
     [:> FormLabel {:html-for priority-id}
      "Priority"]
     [:> Box [:> Select {:id          priority-id
                         :value       priority-uid
                         :size "sm"
                         :placeholder "Select a priority"
                         :on-change   (fn [e]
                                        (let [new-priority (-> e .-target .-value)
                                              priority-ref (str "((" new-priority "))")]
                                          (rf/dispatch [:graph/update-in [:block/uid parent-block-uid] [":task/priority"]
                                                        (fn [db uid] [(graph-ops/build-block-save-op db uid priority-ref)])])))}
              (doall
                (for [{:block/keys [uid string]} allowed-priorities]
                  ^{:key uid}
                  [:option {:value uid}
                   string]))]]]))


(defn task-el
  [_this block-data _callbacks _is-ref?]
  (let [block-uid (:block/uid block-data)]
    (fn [_this _block-data callbacks]
      (let [block           (-> [:block/uid block-uid] reactive/get-reactive-block-document)
            props           (-> block :block/properties)
            title-uid       (-> props (get ":task/title") :block/uid)
            assignee-uid    (-> props (get ":task/assignee") :block/uid)
            priority-uid    (-> props (get ":task/priority") :block/uid)
            _description-uid (-> props (get ":task/description") :block/uid)
            _creator-uid     (-> props (get ":task/creator") :block/uid)
            due-date-uid    (-> props (get ":task/due-date") :block/uid)
            ;; projects-uid  (:block/uid (find-property-block-by-key-name reactive-block ":task/projects"))
            ;; status-uid      (-> props (get ":task/status") :block/uid)
            ;; map the :string key of the return from (find-allowed-statuses) into a vector
            status-options  (->> (shared/find-allowed-statuses)
                                 (map (fn [{:block/keys [string]}]
                                        string)))
            creator         (-> (:block/create block) :event/auth :presence/id)
            time            (-> (:block/create block) :event/time :time/ts)
            created-date    (when time
                              (-> time
                                  t/instant
                                  t/date
                                  (dates/get-day 0)
                                  :title))
            status          (-> (common-db/get-block @db/dsdb [:block/uid  (-> props
                                                                               (get ":task/status")
                                                                               :block/string
                                                                               (common-db/strip-markup "((" "))"))])
                                :block/string)
            title          (-> props (get ":task/title") :block/string)
            assignee        (-> props (get ":task/assignee") :block/string (common-db/strip-markup "[[" "]]"))
            priority        (-> (common-db/get-block @db/dsdb [:block/uid  (-> props
                                                                               (get ":task/priority")
                                                                               :block/string
                                                                               (common-db/strip-markup "((" "))"))])
                                :block/string)
            creator         creator
            description     (-> props (get ":task/description") :block/string)
            due-date        (-> props
                                (get ":task/due-date")
                                :block/string
                                (common-db/strip-markup "[[" "]]"))

            show-assignee?     true
            show-description?  false
            show-priority?     true
            show-creator?      false
            show-created-date? false
            _show-status?      true
            show-due-date?     true]
        [:> HStack {:spacing                  1
                    :gridArea                 "content"
                    :borderRadius             "md"
                    :alignItems               "baseline"
                    :transitionProperty       "colors"
                    :transitionDuration       "fast"
                    :transitionTimingFunction "ease-in-out"
                    :overflow                 "hidden"
                    :align                    "stretch"}
         [:> Taskbox {:status   status
                      :options  status-options
                      :position "relative"
                      :top      "0.2em"
                      :onChange #(events/on-update-status block-uid %)}]
         [:> Box {:flex       "1 1 100%"
                  :py         1
                  :cursor     "text"
                  :lineHeight 1.4}
          [inline-task-title/inline-task-title-2
           callbacks
           block-uid
           title-uid
           ":task/title"
           "Title"
           true
           false]]
         [:> ModalInput {:placement "left-start"
                         :isLazy    true}
          [:> ModalInputTrigger
           [:> Button {:size         "sm"
                       :flex         "1 0 auto"
                       :variant      "ghost"
                       :onClick      #(.. % stopPropagation)
                       :lineHeight   "unset"
                       :whiteSpace   "unset"
                       :px           2
                       :py           1}

            ;; description
            (when (and show-description? description)
              [:> Text {:fontSize "sm" :flexGrow 1 :flexBasis "100%" :m 0 :py 1 :lineHeight 1.4 :color "foreground.secondary"}
               description])

            ;; tasking/assignment
            (when (and show-priority? priority)
              [:> Badge {:size "sm" :variant "primary"}
               priority])
            (when (or due-date assignee)
              [:> Flex {:gap 1 :align "center"}
               (when (and show-assignee? assignee)
                 [:> AvatarGroup {:size "xs"}
                  [:> Avatar {:name assignee}]])
               (when (and show-due-date? due-date)
                 [:> Text {:fontSize "xs"} due-date])])

            ;; provenance
            [:> Flex {:gap 1 :align "center"}
             (when (and show-creator? creator)
               [:> AvatarGroup {:size "xs"}
                [:> Avatar {:name creator}]])
             (when (and show-created-date? created-date)
               [:> Text {:fontSize "xs"} created-date])]
            [:> PencilIcon {:color "foreground.secondary"}]]]
          [:> ModalInputPopover {:popoverContentProps
                                 {:display             "grid"
                                  :onClick             #(.. % stopPropagation)
                                  :gridTemplateColumns "max-content 1fr"
                                  :gap                 2
                                  :py                  2
                                  :px                  4
                                  :maxWidth            "20em"}}
           [:> HStack {:gridColumn "1 / -1" :align "flex-start"}
            [:> Text {:fontSize "sm"
                      :noOfLines 2
                      :color "foreground.secondary"}
             title]]
           [:> Divider {:gridColumn "1 / -1"}]
           [task-priority-view block-uid priority-uid]
           [generic-textarea/generic-textarea-view-for-task-props block-uid assignee-uid ":task/assignee" "Assignee" false false]
           ;; Making assumption that for now we can add due date manually without date-picker.
           [generic-textarea/generic-textarea-view-for-task-props block-uid due-date-uid ":task/due-date" "Due Date" false false]
           [:> Divider {:gridColumn "1 / -1"}]
           [:> Text {:color "foreground.secondary" :fontSize "sm"} "Created by"]
           [:> Flex {:align "center"} [:> Avatar {:size "2xs" :marginInlineEnd 1 :name creator}] [:> Text {:fontSize "sm" :noOfLines 0} creator]]
           [:> Text {:color "foreground.secondary" :fontSize "sm"} "Created"]
           [:> Text {:fontSize "sm"} created-date]]]]))))


(defrecord TaskView
  []

  types/BlockTypeProtocol

  (inline-ref-view
    [_this _block-data _attr _ref-uid _uid _callbacks _with-breadcrumb?]
    (let [block (reactive/get-reactive-block-document [:block/uid _ref-uid])]
      [:> Flex {:display "inline-flex"
                :gap     1}
       [:> Taskbox]
       [:> Text (:block/string block)]]))


  (outline-view
    [_this block-data callbacks]
    [task-el _this block-data callbacks false])


  (supported-transclusion-scopes
    [_this]
    #{:embed})


  (transclusion-view
    [this _block-el block-uid _callback transclusion-scope]
    (let [supported-trans (types/supported-transclusion-scopes this)]
      (if-not (contains? supported-trans transclusion-scope)
        (throw (ex-info (str "Invalid transclusion scope: " (pr-str transclusion-scope)
                             ". Supported transclusion types: " (pr-str supported-trans))
                        {:supported-transclusion-scopes supported-trans
                         :provided-transclusion-scope   transclusion-scope}))
        (let [block (reactive/get-reactive-block-document [:block/uid block-uid])]
          [task-el this block true]))))


  (zoomed-in-view
    [_this _block-data _callbacks])


  (supported-breadcrumb-styles
    [_this]
    #{:string})


  (breadcrumbs-view
    [_this _block-data _callbacks _breadcrumb-style]))


(defmethod dispatcher/block-type->protocol "[[athens/task]]" [_k _args-map]
  (TaskView.))
