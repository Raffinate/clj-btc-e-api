# clj-btc-e-api                                                                                                       
A Clojure library designed to connect and trade on btc-e.com.
## Usage                                                                                                              
### Installation                                                                                                      

[![Clojars Project](http://clojars.org/clj-btc-e-api/latest-version.svg)](http://clojars.org/clj-btc-e-api)

[clj-btc-e-api.core :as btce]

### Documenation

Each API function has it's own docstring. There are
[get-public, get-depth, get-trades, get-ticker, get-fee, trade] functions
to communicate with the stock. Each returns a future. 
Use new-stock function to create your api structure
with your key and secret to use trade api.

### Example                                                                                                   

```clojure                        
user> @(btce/get-ticker :btc-usd)
{:ticker {:vol_cur 4091.90718, :updated 1441444674, :high 226.845, :sell 225.368, :buy 225.504, :low 224, :avg 225.4225, :last 225.504, :server_time 1441444675, :vol 923877.0129}}

user> (def s (btce/new-stock "MY-KEY-BLAH-BLAH" "MY-SECRET-BLAH-BLAH"))
user> @(btce/trade s :getInfo)
{:success 1, :return {:funds {:ppc 132, :btc 123, :cnh 123, :ftc 321, :xpm 231, :usd 1232, :nvc 3213, :ltc 3123, :rur 33211, :nmc 321, :gbp 231, :trc 131, :eur 123}, :rights {:info 1, :trade 0, :withdraw 0}, :transaction_count 31, :open_orders 12, :server_time 1441445408}}
user> @(btce/trade s :TradeHistory :from-id 343140 :end-id 343150 :pair :btc-usd)
{
	"success":1,
	"return":{
		"166830":{
			"pair":"btc_usd",
			"type":"sell",
			"amount":1,
			"rate":450,
			"order_id":343148,
			"is_your_order":1,
			"timestamp":1342445793
		}
	}
}

```

Trade api requires an incremental number each request. 
Default nonce generator use system time to generate nonce, but this
can cause errors on high frequency requests and it looses possible numbers even if you don't trade. 
You can use atom generator or any 0-arity function.

```clojure
user> (def start-number 1)
user> (def s1 (btce/new-stock "MY-KEY-..." "MY-SECRET-..." (btce/atom-nonce start-number)))
user> (def s2 (btce/new-stock "MY-KEY-..." "MY-SECRET-..." (fn [] ... return incremental number each call...)))
```

## License                                                     

Copyright © 2015

Distributed under the Eclipse Public License either version 1.0 or (at 
your option) any later version. 
