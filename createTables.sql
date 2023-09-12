-- Add all your SQL setup statements here.

-- When we test your submission, you can assume that the following base
-- tables have been created and loaded with data.  However, before testing
-- your own code, you will need to create and populate them on your
-- SQLServer instance
--
-- Do not alter the following tables' contents or schema in your code.

-- FLIGHTS(fid int primary key,
 --        month_id int,        -- 1-12
  --       day_of_month int,    -- 1-31
  --       day_of_week_id int,  -- 1-7, 1 = Monday, 2 = Tuesday, etc
  --       carrier_id varchar(7),
  --       flight_num int,
   --      origin_city varchar(34),
   --      origin_state varchar(47),
    --     dest_city varchar(34),
    --     dest_state varchar(46),
    --     departure_delay int, -- in mins
    --     taxi_out int,        -- in mins
    --     arrival_delay int,   -- in mins
    --     canceled int,        -- 1 means canceled
    --     actual_time int,     -- in mins
    --     distance int,        -- in miles
    --     capacity int,
    --     price int            -- in $
    --     )

 --CREATE TABLE CARRIERS(cid varchar(7) primary key,
   --       name varchar(83))

 --CREATE TABLE MONTHS(mid int primary key,
  --      month varchar(9));

 --CREATE TABLE WEEKDAYS(did int primary key,
   --       day_of_week varchar(9));

CREATE TABLE USERS_mhalim3 (
  username varchar(20),
  password varbinary(20),
  salt varbinary(20),
  balance int,
  PRIMARY KEY (username)
);

CREATE TABLE RESERVATIONS_mhalim3 (
  reservation_id int IDENTITY(1,1) not null,
  user_name varchar(20),
  flight_id1 int,
  flight_id2 int,
  isPaid bit,
  PRIMARY KEY (reservation_id),
  FOREIGN KEY (user_name) REFERENCES USERS_mhalim3(username) ON DELETE CASCADE,
  FOREIGN KEY (flight_id1) REFERENCES FLIGHTS(fid),
  FOREIGN KEY (flight_id2) REFERENCES FLIGHTS(fid) ON DELETE CASCADE
);