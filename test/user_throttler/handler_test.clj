(ns user-throttler.handler-test
  (:require [expectations :refer :all]
            [ring.mock.request :as mock]
            [user-throttler.handler :refer :all]
            [datomic.api :as d]))

;; helper function that creates a new empty in memory database
(defn create-empty-in-memory-db []
  (let [uri "datomic:mem://pet-owners-test-db"]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)
          schema (load-file "resources/datomic/schema.edn")]
      (d/transact conn schema)
      conn)))

;; testing the front page
(expect "Hello World"
    (let [response (app (mock/request :get "/"))]
      (:body response)))

;; when we try to access an invalid we expect a 404
(expect 404
    (let [response (app (mock/request :get "/invalid"))]
      (:status response)))

;; the connection should not be false after we tried to ensure the db
(expect false
        (do
          (ensure-db)
          (nil? conn)))

;; adding one user should return him
(expect #{["John"]}
        (with-redefs [conn (create-empty-in-memory-db)]
        (do
          (add-user "John")
          (find-all-users)))
)

;; adding a bunch of owners should return them all
(expect #{["John"] ["Paul"] ["Ringo"]}
        (with-redefs [conn (create-empty-in-memory-db)]
        (do
          (add-user "John")
          (add-user "Paul")
          (add-user "Ringo")
          (find-all-users)))
)

;; adding quota for a user should allow us to find them
(expect #{[(.toBigInteger 500N)]}
        (with-redefs [conn (create-empty-in-memory-db)]
          (do
            (add-user "John")
            (add-user-quota "John" 500N)
            (find-quota-for-user "John")
          )))

;; adding a 0 quota for a user and asking wheter or not he has requests
;; left should return false
(expect false
        (with-redefs [conn (create-empty-in-memory-db)]
          (do
            (add-user "John")
            (add-user-quota "John" 0N)
            (has-requests-left "John")
          )))

;; inversly a user with 1000 quotes should have quotes left
(expect true
        (with-redefs [conn (create-empty-in-memory-db)]
          (do
            (add-user "John")
            (add-user-quota "John" 1000N)
            (has-requests-left "John")
          )))

;; a user with only 1 quotes should have no request left
;; after making one
(expect false
        (with-redefs [conn (create-empty-in-memory-db)]
          (do
            (add-user "John")
            (add-user-quota "John" 1N)
            (add-user-request "John" "test-request")
            (has-requests-left "John")
          )))

;; a user that makes three request should return a request count of 3
(expect 3
        (with-redefs [conn (create-empty-in-memory-db)]
          (do
            (add-user "John")
            (add-user-quota "John" 100N)
            (add-user-request "John" "test-request")
            (add-user-request "John" "test-request")
            (add-user-request "John" "test-request")
            (count (find-request-ids-for-user "John"))
          )))

;; adding requests for a user should return them
;; adding a bunch of owners should return them all
(expect #{["songs"] ["albums"] ["live-cds"]}
        (with-redefs [conn (create-empty-in-memory-db)]
        (do
            (add-user "John")
            (add-user-quota "John" 100N)
            (add-user-request "John" "songs")
            (add-user-request "John" "albums")
            (add-user-request "John" "live-cds")
            (find-all-requests-for-user "John")))
)
