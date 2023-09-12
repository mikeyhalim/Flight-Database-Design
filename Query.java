package flightapp;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  // Canned queries
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;

  // Instance variables
  private boolean isLoggedIn;
  private String userName;
  private List<List<Integer>> itineraries;

  // Hash constraints
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Table clearing Stmts
  private static final String SQL_CLEAR_RESERVATIONS = "TRUNCATE TABLE RESERVATIONS_mhalim3";
  private static final String SQL_CLEAR_USERS = "DELETE FROM USERS_mhalim3";
  private static final String SQL_RESEED_RESERVATIONS = "DBCC CHECKIDENT ('RESERVATIONS_mhalim3', RESEED, ?)";
  private static final String SQL_CLEAR_RESERVATIONS_FOR_USERS = "DELETE FROM RESERVATIONS_mhalim3 WHERE user_name IN (SELECT username FROM USERS_mhalim3)";
  private PreparedStatement clearReservationsStmt;
  private PreparedStatement clearUsersStmt;
  private PreparedStatement reseedReservationsStmt;
  private PreparedStatement clearReservationsForUsersStmt;

  // Login Stmt
  private static final String SQL_CHECK_USER = "SELECT password, salt FROM USERS_mhalim3 WHERE username = ?";
  private PreparedStatement checkUserStmt;

  // Create user Stmt
  private static final String SQL_CREATE_USER = "INSERT INTO USERS_mhalim3 (username, password, salt, balance) VALUES (?, ?, ?, ?)";
  private PreparedStatement createUserStmt;

  // Search Stmts
  private static final String SQL_DIRECT =
          "SELECT TOP ( ? ) fid,day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price " +
                  "FROM Flights " +
                  "WHERE origin_city = ? " +
                  "  AND dest_city = ? " +
                  "  AND day_of_month = ? " +
                  "  AND canceled = 0 " +
                  "ORDER BY actual_time ASC";
  private PreparedStatement searchDirectStmt;
  private static final String SQL_INDIRECT =
          "SELECT * FROM " +
          "(SELECT TOP ( ? ) * FROM " +
          "(SELECT 1 AS num, " +
          "F.actual_time AS actual_time, " +
          "F.fid AS fid1, " +
          "F.day_of_month AS d_o_m1, " +
          "F.carrier_id AS c_i1, " +
          "F.flight_num AS f_n1, " +
          "F.origin_city AS o_c1, " +
          "F.dest_city AS d_c1, " +
          "F.capacity AS c1, " +
          "F.price AS p1, " +
          "F.actual_time AS t1, " +
          "NULL AS fid2, " +
          "NULL AS d_o_m2, " +
          "NULL AS c_i2, " +
          "NULL AS f_n2, " +
          "NULL AS o_c2, " +
          "NULL AS d_c2, " +
          "NULL AS c2, " +
          "NULL AS p2, " +
          "NULL AS t2 " +
          "FROM Flights F " +
          "WHERE F.origin_city = ? AND " +
          "F.dest_city = ? AND " +
          "F.day_of_month = ? AND " +
          "F.canceled = 0 " +
          "UNION " +
          "SELECT 2 AS num, " +
          "(F1.actual_time + F2.actual_time) AS actual_time, " +
          "F1.fid AS fid1, " +
          "F1.day_of_month AS d_o_m1, " +
          "F1.carrier_id AS c_i1, " +
          "F1.flight_num AS f_n1, " +
          "F1.origin_city AS o_c1, " +
          "F1.dest_city AS d_c1, " +
          "F1.capacity AS c1, " +
          "F1.price AS p1, " +
          "F1.actual_time AS t1, " +
          "F2.fid AS fid2, " +
          "F2.day_of_month AS d_o_m2, " +
          "F2.carrier_id AS c_i2, " +
          "F2.flight_num AS f_n2, " +
          "F2.origin_city AS o_c2, " +
          "F2.dest_city AS d_c2, " +
          "F2.capacity AS c2, " +
          "F2.price AS p2, " +
          "F2.actual_time AS t2 " +
          "FROM Flights F1, Flights F2 " +
          "WHERE F1.dest_city = F2.origin_city AND " +
          "F1.origin_city = ? AND " +
          "F2.dest_city = ? AND " +
          "F1.day_of_month = ? AND " +
          "F2.day_of_month = ? AND " +
          "F1.canceled = 0 AND " +
          "F2.canceled = 0 AND " +
          "F1.month_id = F2.month_id" +
          ") AS t " +
          "ORDER BY num, actual_time ASC" +
          ") AS m " +
          "ORDER BY actual_time";
  private PreparedStatement searchIndirectStmt;

  // Pay Stmts
  private static final String SQL_FIND_RESERVATION = "select sum(price) as price from (select flight_id1, flight_id2 from RESERVATIONS_mhalim3 WHERE reservation_id = ? AND user_name = ? "
          + " AND isPaid = 0) t, FLIGHTS f WHERE t.flight_id1 = f.fid OR t.flight_id2 = f.fid";
  private PreparedStatement findReservationStmt;
  private static final String SQL_FIND_BALANCE = "select balance from USERS_mhalim3 where username = ?";
  private PreparedStatement findBalanceStmt;
  private static final String SQL_UPDATE_BALANCE = "UPDATE USERS_mhalim3 SET balance = ? WHERE username = ?";
  private PreparedStatement updateBalanceStmt;
  private static final String SQL_IS_PAID = "UPDATE RESERVATIONS_mhalim3 SET isPaid = 1 WHERE reservation_id = ?";
  private PreparedStatement isPaidStmt;

  // Reservation Stmts
  private static final String SQL_RESERVATIONS = "select * from RESERVATIONS_mhalim3 where user_name = ?";
  private PreparedStatement getReservationsStmt;
  private static final String SQL_FIND_FID = "select day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price from FLIGHTS where fid = ?";
  private PreparedStatement findFIDStmt;
  private static final String SQL_COUNT_RESERVATIONS = "SELECT COUNT(reservation_id) FROM RESERVATIONS_mhalim3";
  private PreparedStatement countReservationsStmt;
  private static final String SQL_MAX_RESERVATION = "SELECT MAX(reservation_id) FROM RESERVATIONS_mhalim3";
  PreparedStatement getMaxReservationIDStmt;



  // Book Stmts
  private static final String SQL_CHECK_SAME_DAY = "select count(*) as num from RESERVATIONS_mhalim3 r JOIN FLIGHTS f ON r.flight_id1 = f.fid "
          + " where user_name = ? AND f.day_of_month = ?";
  private PreparedStatement checkSameDayStmt;
  private static final String SQL_CHECK_FLIGHT_RESERVATION_NUM = "select count(*) as num from RESERVATIONS_mhalim3 where (flight_id1 = ? OR flight_id2 = ?)";
  private PreparedStatement checkFlightReservationNumStmt;
  private static final String SQL_CREATE_RESERVATION = "insert into RESERVATIONS_mhalim3 (user_name,flight_id1,flight_id2,isPaid) values (?,?,?,0)";
  private PreparedStatement createReservationStmt;
  protected Query() throws SQLException, IOException {
    isLoggedIn = false;
    userName = null;
    itineraries = null;
    PreparedStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearReservationsStmt.executeUpdate();
      clearReservationsForUsersStmt.executeUpdate(); // add this line
      clearUsersStmt.executeUpdate();
      reseedReservationsStmt.setInt(1, 1);
      reseedReservationsStmt.executeUpdate();
    } catch (SQLException e) {
      handleSQLException(e);
    }
  }


  /*
   * prepare all the SQL Stmts in this method.
   */
  private void PreparedStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    clearReservationsStmt = conn.prepareStatement(SQL_CLEAR_RESERVATIONS);
    clearUsersStmt = conn.prepareStatement(SQL_CLEAR_USERS);
    reseedReservationsStmt = conn.prepareStatement(SQL_RESEED_RESERVATIONS);
    checkUserStmt = conn.prepareStatement(SQL_CHECK_USER);
    createUserStmt = conn.prepareStatement(SQL_CREATE_USER);
    searchDirectStmt = conn.prepareStatement(SQL_DIRECT);
    searchIndirectStmt = conn.prepareStatement(SQL_INDIRECT);
    getReservationsStmt = conn.prepareStatement(SQL_RESERVATIONS);
    findReservationStmt = conn.prepareStatement(SQL_FIND_RESERVATION);
    findBalanceStmt = conn.prepareStatement(SQL_FIND_BALANCE);
    updateBalanceStmt = conn.prepareStatement(SQL_UPDATE_BALANCE);
    isPaidStmt = conn.prepareStatement(SQL_IS_PAID);
    findFIDStmt = conn.prepareStatement(SQL_FIND_FID);
    checkSameDayStmt = conn.prepareStatement(SQL_CHECK_SAME_DAY);
    checkFlightReservationNumStmt = conn.prepareStatement(SQL_CHECK_FLIGHT_RESERVATION_NUM);
    createReservationStmt = conn.prepareStatement(SQL_CREATE_RESERVATION, Statement.RETURN_GENERATED_KEYS);
    clearReservationsForUsersStmt = conn.prepareStatement(SQL_CLEAR_RESERVATIONS_FOR_USERS);
    countReservationsStmt = conn.prepareStatement(SQL_COUNT_RESERVATIONS);
    getMaxReservationIDStmt = conn.prepareStatement(SQL_MAX_RESERVATION);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n".  For all
   *         other errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    if (isLoggedIn) {
      return "User already logged in\n";
    }
    try {
      checkUserStmt.clearParameters();
      checkUserStmt.setString(1, username);
      ResultSet results = checkUserStmt.executeQuery();
      if (results.next()) {
        byte[] hash = results.getBytes("password");
        byte[] salt = results.getBytes("salt");
        if (passwordMatchesHash(password, salt, hash)) {
          isLoggedIn = true;
          userName = username;
          itineraries = null;
          return String.format("Logged in as %s\n", username);
        }
      }
    } catch (SQLException e) {
      handleSQLException(e);
    } finally {
      checkDanglingTransaction();
    }

    return "Login failed\n";
  }
  private boolean passwordMatchesHash(String password, byte[] salt, byte[] expectedHash) {
    try {
      KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] actualHash = factory.generateSecret(spec).getEncoded();
      return Arrays.equals(expectedHash, actualHash);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      if (initAmount < 0) {
        return "Failed to create user\n";
      }

      // Start a transaction
      conn.setAutoCommit(false);

      // Check if user already exists
      checkUserStmt.clearParameters();
      checkUserStmt.setString(1, username);
      ResultSet results = checkUserStmt.executeQuery();
      if (results.next()) {
        // User already exists, rollback transaction
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }

      // Generate salt and hash from password
      SecureRandom random = new SecureRandom();
      byte[] salt = new byte[16];
      random.nextBytes(salt);
      KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] hash = factory.generateSecret(spec).getEncoded();

      // Create user with given parameters
      createUserStmt.clearParameters();
      createUserStmt.setString(1, username);
      createUserStmt.setBytes(2, hash);
      createUserStmt.setBytes(3, salt);
      createUserStmt.setInt(4, initAmount);
      createUserStmt.executeUpdate();

      // Commit transaction
      conn.commit();

      return String.format("Created user %s\n", username);

      // Catch exceptions and deadlock handling
    } catch (SQLException | NoSuchAlgorithmException | InvalidKeySpecException e) {
        if (isDeadlock((SQLException) e)) {
          try {
            conn.rollback();
            conn.setAutoCommit(true);
          } catch (SQLException f) {
            handleSQLException(f);
          }
          // Retry the transaction
          return transaction_createCustomer(username, password, initAmount);
        }
        else {
          try {
            conn.rollback();
            conn.setAutoCommit(true);
          } catch (SQLException f) {
            handleSQLException(f);
          }
        }
        return "Failed to create user\n";
      } finally {
        checkDanglingTransaction();
      }
    }



  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given
   * day of the month. If {@code directFlight} is true, it only searches for direct flights,
   * otherwise is searches for direct flights and flights with two "hops." Only searches for up
   * to the number of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return, must be positive
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth, int numberOfItineraries) {
    StringBuffer sb = new StringBuffer();
    try {
      // executes the direct flight SQL Stmt
      if (directFlight) {
        try {
        searchDirectStmt.clearParameters();
        searchDirectStmt.setInt(1, numberOfItineraries);
        searchDirectStmt.setString(2, originCity);
        searchDirectStmt.setString(3, destinationCity);
        searchDirectStmt.setInt(4, dayOfMonth);

        ResultSet directResultSet = searchDirectStmt.executeQuery();
        itineraries = new ArrayList<>();

        int itineraryCount = 0;
          while (directResultSet.next()) {
            int fid = directResultSet.getInt("fid");
            int dayOfMonth1 = directResultSet.getInt("day_of_month");
            String carrierId = directResultSet.getString("carrier_id");
            String flightNum = directResultSet.getString("flight_num");
            String originCity1 = directResultSet.getString("origin_city");
            String destCity = directResultSet.getString("dest_city");
            int time = directResultSet.getInt("actual_time");
            int capacity = directResultSet.getInt("capacity");
            int price = directResultSet.getInt("price");

            itineraries.add(Arrays.asList(fid, capacity, -1, -1, dayOfMonth));

            sb.append(String.format("Itinerary %d: 1 flight(s), %d minutes\n", itineraryCount++, time));
            sb.append(String.format("ID: %d Day: %d Carrier: %s Number: %s Origin: %s Dest: %s Duration: %d Capacity: %d Price: %d\n",
                    fid, dayOfMonth1, carrierId, flightNum, originCity1, destCity, time, capacity, price));
          }
        directResultSet.close();

      } catch(SQLException e){
          handleSQLException(e);
      }
        // if the flight is not direct, executes indirect SQL Stmt
      } else {
        try {
          searchIndirectStmt.clearParameters();
          searchIndirectStmt.setInt(1, numberOfItineraries);
          searchIndirectStmt.setString(2, originCity);
          searchIndirectStmt.setString(3, destinationCity);
          searchIndirectStmt.setInt(4, dayOfMonth);
          searchIndirectStmt.setString(5, originCity);
          searchIndirectStmt.setString(6, destinationCity);
          searchIndirectStmt.setInt(7, dayOfMonth);
          searchIndirectStmt.setInt(8, dayOfMonth);

          ResultSet indirectResultSet = searchIndirectStmt.executeQuery();
          itineraries = new ArrayList<>();

          int itineraryCount = 0;
          while (indirectResultSet.next()) {
            int sumTime = indirectResultSet.getInt("actual_time");
            int numFlights = indirectResultSet.getInt("fid2") != 0 ? 2 : 1;
            int fid1 = indirectResultSet.getInt("fid1");
            int dayOfMonth1 = indirectResultSet.getInt("d_o_m1");
            String carrierId1 = indirectResultSet.getString("c_i1");
            String flightNum1 = indirectResultSet.getString("f_n1");
            String originCity1 = indirectResultSet.getString("o_c1");
            String destCity1 = indirectResultSet.getString("d_c1");
            int time1 = indirectResultSet.getInt("t1");
            int capacity1 = indirectResultSet.getInt("c1");
            int price1 = indirectResultSet.getInt("p1");

            sb.append("Itinerary " + (itineraryCount++) + ": " + numFlights + " flight(s), " + sumTime + " minutes\n");
            sb.append("ID: " + fid1 + " Day: " + dayOfMonth1 + " Carrier: " + carrierId1
                    + " Number: " + flightNum1 + " Origin: " + originCity1 + " Dest: " + destCity1
                    + " Duration: " + time1 + " Capacity: " + capacity1 + " Price: " + price1 + "\n");

            if (numFlights == 2) {
              int fid2 = indirectResultSet.getInt("fid2");
              int dayOfMonth2 = indirectResultSet.getInt("d_o_m2");
              String carrierId2 = indirectResultSet.getString("c_i2");
              String flightNum2 = indirectResultSet.getString("f_n2");
              String originCity2 = indirectResultSet.getString("o_c2");
              String destCity2 = indirectResultSet.getString("d_c2");
              int time2 = indirectResultSet.getInt("t2");
              int capacity2 = indirectResultSet.getInt("c2");
              int price2 = indirectResultSet.getInt("p2");
              itineraries.add(List.of(fid1, capacity1, fid2, capacity2, dayOfMonth1));

              sb.append("ID: " + fid2 + " Day: " + dayOfMonth2 + " Carrier: " + carrierId2
                      + " Number: " + flightNum2 + " Origin: " + originCity2 + " Dest: " + destCity2
                      + " Duration: " + time2 + " Capacity: " + capacity2 + " Price: " + price2
                      + "\n");
            } else {
              itineraries.add(List.of(fid1, capacity1, -1, -1, dayOfMonth1));
            }
          }
          indirectResultSet.close();

        } catch (SQLException e) {
          handleSQLException(e);
        }
      }
      if (itineraries.size() == 0) {
        return "No flights match your selection\n";
      }
      return sb.toString();
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search
   *                    in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged
   *         in\n". If the user is trying to book an itinerary with an invalid ID or without
   *         having done a search, then return "No such itinerary {@code itineraryId}\n". If the
   *         user already has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same day\n". For all
   *         other errors, return "Booking failed\n".
   *
   *         If booking succeeds, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from
   *         1 and increments by 1 each time a successful reservation is made by any user in
   *         the system.
   */
  public String transaction_book(int itineraryId) {
    if (!isLoggedIn) {
      return "Cannot book reservations, not logged in\n";
    }
    if (itineraries == null || itineraryId >= itineraries.size() || itineraryId < 0) {
      return "No such itinerary " + itineraryId + "\n";
    }
    try {
      conn.setAutoCommit(false);

      checkSameDayStmt.clearParameters();
      checkSameDayStmt.setString(1, userName);
      checkSameDayStmt.setInt(2, itineraries.get(itineraryId).get(4));
      ResultSet res_num = checkSameDayStmt.executeQuery();
      res_num.next();
      int num = res_num.getInt("num");
      res_num.close();

      if (num > 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "You cannot book two flights in the same day\n";
      }

      checkFlightReservationNumStmt.clearParameters();
      checkFlightReservationNumStmt.setInt(1, itineraries.get(itineraryId).get(0));
      checkFlightReservationNumStmt.setInt(2, itineraries.get(itineraryId).get(0));

      ResultSet res = checkFlightReservationNumStmt.executeQuery();
      res.next();
      int subtract = res.getInt("num");
      res.close();
      int left_capacity = itineraries.get(itineraryId).get(1) - subtract;

      if (itineraries.get(itineraryId).get(2) >= 0) {
        checkFlightReservationNumStmt.clearParameters();
        checkFlightReservationNumStmt.setInt(1, itineraries.get(itineraryId).get(2));
        checkFlightReservationNumStmt.setInt(2, itineraries.get(itineraryId).get(2));
        res = checkFlightReservationNumStmt.executeQuery();
        res.next();
        int subtract2 = res.getInt("num");
        res.close();
        left_capacity = Math.min(itineraries.get(itineraryId).get(3) - subtract2, left_capacity);
      }

      if (left_capacity > 0) {
        createReservationStmt.clearParameters();
        createReservationStmt.setString(1, userName);
        createReservationStmt.setInt(2, itineraries.get(itineraryId).get(0));
        int fid2 = itineraries.get(itineraryId).get(2);
        if (fid2 > 0) {
          createReservationStmt.setInt(3, fid2);
        } else {
          createReservationStmt.setNull(3, java.sql.Types.INTEGER);
        }
        createReservationStmt.executeUpdate();
        ResultSet rs = createReservationStmt.getGeneratedKeys();
        rs.next();
        int nextReservationID = getMaxReservationID();
        rs.close();

        conn.commit();
        conn.setAutoCommit(true);
        return "Booked flight(s), reservation ID: " + nextReservationID + "\n";
      }

      conn.rollback();
      conn.setAutoCommit(true);
      return "Booking failed\n";

    } catch (SQLException e) {
      if (isDeadlock(e)) {
        try {
          conn.rollback();
          conn.setAutoCommit(true);
        } catch (SQLException f) {
          handleSQLException(f);
        }
        // Retry the transaction
        return transaction_book(itineraryId);
      }
      else {
        try {
          conn.rollback();
          conn.setAutoCommit(true);
        } catch (SQLException f) {
          handleSQLException(f);
        }
      }
      return "Booking failed\n";
    } finally {
      checkDanglingTransaction();
    }
  }


  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n". If the
   *         reservation is not found / not under the logged in user's name, then return
   *         "Cannot find unpaid reservation [reservationId] under user: [username]\n".  If
   *         the user does not have enough money in their account, then return
   *         "User has only [balance] in account but itinerary costs [cost]\n".  For all other
   *         errors, return "Failed to pay for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    if (!isLoggedIn) {
      return "Cannot pay, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      findReservationStmt.clearParameters();
      findReservationStmt.setInt(1, reservationId);
      findReservationStmt.setString(2, userName);
      ResultSet rs1 = findReservationStmt.executeQuery();
      rs1.next();
      int price = rs1.getInt("price");
      rs1.close();

      if (price == 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot find unpaid reservation " + reservationId + " under user: " + userName + "\n";
      }

      findBalanceStmt.clearParameters();
      findBalanceStmt.setString(1, userName);
      ResultSet rs2 = findBalanceStmt.executeQuery();

      if (!rs2.next()) {
        rs2.close();
        conn.rollback();
        conn.setAutoCommit(true);
        throw new SQLException("Failed to find balance for user " + userName);
      }

      int balance = rs2.getInt("balance");
      rs2.close();

      if (balance < price) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "User has only " + balance + " in account but itinerary costs " + price + "\n";
      }

      updateBalanceStmt.clearParameters();
      updateBalanceStmt.setInt(1, balance - price);
      updateBalanceStmt.setString(2, userName);
      updateBalanceStmt.executeUpdate();
      isPaidStmt.clearParameters();
      isPaidStmt.setInt(1, reservationId);
      isPaidStmt.executeUpdate();

      conn.commit();
      conn.setAutoCommit(true);
      return "Paid reservation: " + reservationId + " remaining balance: " + (balance - price) + "\n";

    } catch (SQLException e) {
      if (isDeadlock(e)) {
        try {
          conn.rollback();
          conn.setAutoCommit(true);
        } catch (SQLException f) {
          handleSQLException(f);
        }
        // Retry the transaction
        return transaction_pay(reservationId);
      }
      else {
        try {
          conn.rollback();
          conn.setAutoCommit(true);
        } catch (SQLException f) {
          handleSQLException(f);
        }
      }
      return "Failed to pay for reservation " + reservationId + "\n";
    } finally {
      checkDanglingTransaction();
    }
  }


  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    if (!isLoggedIn) {
      return "Cannot view reservations, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      getReservationsStmt.clearParameters();
      getReservationsStmt.setString(1, userName);
      ResultSet rs = getReservationsStmt.executeQuery();
      int i = 0;
      StringBuffer sb = new StringBuffer();
      while (rs.next()) {
        i++;
        int reservation_id = rs.getInt("reservation_id");
        int fid1 = rs.getInt("flight_id1");
        boolean isPaid = rs.getBoolean("isPaid");
        int nextReservationID = getMaxReservationID();

        findFIDStmt.clearParameters();
        findFIDStmt.setInt(1, fid1);
        ResultSet rs2 = findFIDStmt.executeQuery();
        rs2.next();
        int result_dayOfMonth = rs2.getInt("day_of_month");
        String result_carrierId = rs2.getString("carrier_id");
        int result_flightNum = rs2.getInt("flight_num");
        String result_originCity = rs2.getString("origin_city");
        String result_destCity = rs2.getString("dest_city");
        int result_time = rs2.getInt("actual_time");
        int result_capacity = rs2.getInt("capacity");
        int result_price = rs2.getInt("price");
        rs2.close();

        sb.append("Reservation " + reservation_id + " paid: " + isPaid + ":\n" + "ID: " + fid1 + " Day: "
                + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: " + result_flightNum + " Origin: "
                + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time + " Capacity: "
                + result_capacity + " Price: " + result_price + "\n");

        int fid2 = rs.getInt("flight_id2");
        if (!rs.wasNull()) {
          findFIDStmt.clearParameters();
          findFIDStmt.setInt(1, fid2);
          rs2 = findFIDStmt.executeQuery();
          rs2.next();
          result_dayOfMonth = rs2.getInt("day_of_month");
          result_carrierId = rs2.getString("carrier_id");
          result_flightNum = rs2.getInt("flight_num");
          result_originCity = rs2.getString("origin_city");
          result_destCity = rs2.getString("dest_city");
          result_time = rs2.getInt("actual_time");
          result_capacity = rs2.getInt("capacity");
          result_price = rs2.getInt("price");
          rs2.close();

          sb.append("ID: " + fid2 + " Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: "
                  + result_flightNum + " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: "
                  + result_time + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
        }
      }

      if (i == 0) {
        rs.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }
      rs.close();
      conn.commit();
      conn.setAutoCommit(true);
      return sb.toString();

    } catch (SQLException e) {
      if (isDeadlock(e)) {
        try {
          conn.rollback();
          conn.setAutoCommit(true);
        } catch (SQLException f) {
          handleSQLException(f);
        }
        // Retry the transaction
        return transaction_reservations();
      }
      handleSQLException(e);
      return "Failed to retrieve reservations\n";
    } finally {
      checkDanglingTransaction();
    }
  }
  private int getMaxReservationID() throws SQLException {
    getMaxReservationIDStmt.clearParameters();
    ResultSet rs = getMaxReservationIDStmt.executeQuery();
    rs.next();
    int maxID = rs.getInt(1);
    rs.close();
    return maxID;
  }
  private void handleSQLException(SQLException e) {
    e.printStackTrace();
    // Handle the exception as appropriate for the application
  }


  /**
   * Example utility function that uses prepared Stmts
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
