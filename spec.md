base layer data model:


SOD holding: holding id, date, instrument id, position

price: date, instrument id, price

instrument: instrument id, instrument name, country of risk, country of domincile, sector, sub-sectors. 

intraday cash: date, instrument id, quantity

Order: order id, instrument id, account id, date, order quanity, filled_quantity

account: account id, account name. 

Mock data generator: 

each data model should has a kafka topic and a mock data generator should be able to generate for each data model. the mock data generate should do following for each data type, 

static data (no date): 
    account: generate 100 accounts and name is some sort of equity strategy names. 
    instruments: generate 500 equity instrument names

SOD date driven data (when sod event for each day happens, this triggers following events to publish to kafka):
    SOD holding: for each accounts, mock about 500 holdings with mixed of instrument. 


intraday update date (these events happens randomly during the day)

    intraday cash: cash is also a instrument, i.e. USD or GBP

    orders: order has create event, also filled events (when the filled quantity increase)



