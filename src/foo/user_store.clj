(ns foo.user-store
  (:require   [com.stuartsierra.component :as component :refer (using)]
              [cylon.user.protocols :refer (UserStore get-user)]
              [cylon.token-store.protocols :refer (TokenStore create-token! get-token-by-id renew-token! purge-token! dissoc-token! merge-token!)]
              [cylon.user.totp :as t :refer (OneTimePasswordStore set-totp-secret get-totp-secret)]
              [plumbing.core :refer (<-)]))


(defrecord MyUserStore [host port dbname user password token-store]
  component/Lifecycle
  (start [this]
    #_(assoc this
      :connection
      {:subprotocol "postgresql"
       :classname "org.postgresql.Driver"
       :subname (format "//%s:%d/%s" host port dbname)
       :user user
       :password password})
    this)
  (stop [this] this)

  UserStore
  (create-user! [this uid {:keys [hash salt]} email user-details]
    (create-token! token-store uid {:id uid
                                    :name (:name user-details)
                                    :email email
                                    :password_hash hash
                                    :password_salt salt
                                    :role "user"}))

  (get-user [this uid]
    (when-let [row (-> token-store :tokens deref (get uid))]
      {:uid (:id row)
       :name (:name row)
       :email (:email row)}))

  (get-user-password-hash [this uid]
    (when-let [row (get-user this uid)]
      {:hash (:password_hash row)
       :salt (:password_salt row)}))

  (set-user-password-hash! [this uid {:keys [hash salt]}]
    (merge-token! token-store uid
                  {:hash (:password_hash hash)
                   :salt (:password_salt salt)}))

  (get-user-by-email [this email]

    (when-let [row (->>
      (-> token-store :tokens deref vals)
      (filter #(= email (:email %))))]
      {:uid (:id row)
       :name (:name row)
       :email (:email row)}))

  (delete-user! [this uid]
    (purge-token! token-store uid))

  (verify-email! [this uid]
    (merge-token! token-store uid {:email_verified true}))


  OneTimePasswordStore
  (set-totp-secret [this identity encrypted-secret]
    #_(j/insert! (:connection this) :totp_secrets {:user_id identity :secret encrypted-secret})
    )

  (get-totp-secret [this identity]
    #_(:secret (first (j/query (:connection this) ["SELECT secret from totp_secrets WHERE user_id = ?" identity])))
   ))


(defn new-user-store
  [& {:as opts}]
  (->> opts
       map->MyUserStore
       (<- (using [:token-store]))))