package flightapp;

import java.io.IOException;
import java.net.IDN;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private String currentUser;
  private static final String FLIGHT_CAPACITY_SQL = "SELECT at.num_seats FROM Flights f, Aircraft_Types at, N_Numbers n " +
                                                    "WHERE f.fid = ? AND f.tail_num = n.n_number AND n.mfr_mdl_code = at.atid";
  private PreparedStatement flightCapacityStmt;
  private PreparedStatement loginStmt;
  private static final String LOGIN_SQL = "SELECT password FROM Users_jisung WHERE username = ?";
  private PreparedStatement createStmt;
  private static final String CREATE_SQL = "INSERT INTO Users_jisung (username, password, balance) VALUES (LOWER(?), ?, ?)";
  private PreparedStatement checkUserStmt;
  private static final String CHECK_USER_SQL = "SELECT username FROM Users_jisung WHERE LOWER(username) = LOWER(?)";
  private PreparedStatement directStmt;
  private static final String DIRECT_SQL = "SELECT f.fid, f.day_of_month, f.cid, f.op_carrier_flight_num, " +
                                            "f.origin_city, f.dest_city, f.duration_mins, at.num_seats, f.price " +
                                            "FROM Flights f, Aircraft_Types at, N_Numbers n " +
                                            "WHERE f.origin_city = ? AND f.dest_city = ? AND f.day_of_month = ? " +
                                            "AND f.cancelled = 0 " +
                                            "AND f.tail_num = n.n_number " +
                                            "AND n.mfr_mdl_code = at.atid " +
                                            "ORDER BY f.duration_mins, f.fid " +
                                            "LIMIT ?";
  private PreparedStatement indirectStmt;
  private static final String INDIRECT_SQL = "SELECT f1.fid as fid1, f1.day_of_month as day1, f1.cid as cid1, " +
                                              "f1.op_carrier_flight_num as num1, f1.origin_city as origin1, " +
                                              "f1.dest_city as stopover, f1.duration_mins as dur1, " +
                                              "at1.num_seats as cap1, f1.price as price1, " +
                                              "f2.fid as fid2, f2.day_of_month as day2, f2.cid as cid2, " +
                                              "f2.op_carrier_flight_num as num2, f2.dest_city as dest2, " +
                                              "f2.duration_mins as dur2, at2.num_seats as cap2, f2.price as price2 " +
                                              "FROM Flights f1, Flights f2, Aircraft_Types at1, Aircraft_Types at2, " +
                                              "N_Numbers n1, N_Numbers n2 " +
                                              "WHERE f1.origin_city = ? AND f2.dest_city = ? " +
                                              "AND f1.dest_city = f2.origin_city " +
                                              "AND f1.day_of_month = ? AND f2.day_of_month = ? " +
                                              "AND f1.cancelled = 0 AND f2.cancelled = 0 " +
                                              "AND f1.tail_num = n1.n_number AND n1.mfr_mdl_code = at1.atid " +
                                              "AND f2.tail_num = n2.n_number AND n2.mfr_mdl_code = at2.atid " +
                                              "ORDER BY (f1.duration_mins + f2.duration_mins), f1.fid, f2.fid " +
                                              "LIMIT ?";

  private static final String BOOK_SQL = "INSERT INTO Reservations_jisung (rid, paid, username, iid) VALUES (?, ?, ?, ?)";
  private PreparedStatement bookStmt;
  private static final String PAY_SQL = "UPDATE Reservations_jisung SET paid = 1 WHERE rid = ? AND username = ?";
  private PreparedStatement payStmt;
  private static final String RESERVATIONS_SQL = "SELECT r.rid, r.paid, i.* FROM Reservations_jisung r " +
                                                 "JOIN Itineraries_jisung i ON r.iid = i.iid " +
                                                 "WHERE r.username = ? ORDER BY r.rid";
  private PreparedStatement reservationsStmt;
  private static final String ITINERARY_PRICE_SQL = "SELECT price FROM Itineraries_jisung WHERE iid = ?";
  private PreparedStatement itineraryPriceStmt;
  private static final String USER_BALANCE_SQL = "SELECT balance FROM Users_jisung WHERE username = ?";
  private PreparedStatement userBalanceStmt;
  private static final String UPDATE_BALANCE_SQL = "UPDATE Users_jisung SET balance = ? WHERE username = ?";
  private PreparedStatement updateBalanceStmt;
  private static final String CAPACITY_SQL = "SELECT COUNT(*) FROM Reservations_jisung r " +
                                             "JOIN Itineraries_jisung i ON r.iid = i.iid " +
                                             "WHERE (i.fid1 = ? OR i.fid2 = ?)";
  private PreparedStatement capacityStmt;
  private List<Itinerary> recents = new ArrayList<>();

  //
  // Instance variables
  //


  protected Query() throws SQLException, IOException {
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      String[] tables = {"Reservations_jisung", "Itineraries_jisung", "Users_jisung"};
      for (String table : tables) {
        String sql = "DELETE FROM " + table + ";";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.executeUpdate();
        ps.close();
      }
    } catch (Exception e) {
      // e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    // TODO: YOUR CODE HERE
    loginStmt = conn.prepareStatement(LOGIN_SQL);
    createStmt = conn.prepareStatement(CREATE_SQL);
    checkUserStmt = conn.prepareStatement(CHECK_USER_SQL);
    directStmt = conn.prepareStatement(DIRECT_SQL);
    indirectStmt = conn.prepareStatement(INDIRECT_SQL);
    bookStmt = conn.prepareStatement(BOOK_SQL);
    payStmt = conn.prepareStatement(PAY_SQL);
    reservationsStmt = conn.prepareStatement(RESERVATIONS_SQL);
    itineraryPriceStmt = conn.prepareStatement(ITINERARY_PRICE_SQL);
    userBalanceStmt = conn.prepareStatement(USER_BALANCE_SQL);
    updateBalanceStmt = conn.prepareStatement(UPDATE_BALANCE_SQL);
    capacityStmt = conn.prepareStatement(CAPACITY_SQL);
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    // TODO: YOUR CODE HERE
    try {
      if (currentUser != null) {
        return "User already logged in\n";
      }
      loginStmt.clearParameters();
      loginStmt.setString(1, username);
      ResultSet set = loginStmt.executeQuery();
      if (!set.next()) {
        set.close();
        return "Login failed\n";
      }
      byte[] hold = set.getBytes("password");
      set.close();

      if (PasswordUtils.plaintextMatchesSaltedHash(password, hold)) {
        currentUser = username;
        return "Logged in as " + username + "\n";
      } else {
        return "Login failed\n";
      }
    } catch (SQLException e) {
      // e.printStackTrace();
      return "Login failed\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    // TODO: YOUR CODE HERE
    try{
      if (initAmount < 0) {
            return "Failed to create user\n";
        }
      checkUserStmt.clearParameters();
      checkUserStmt.setString(1, username.toLowerCase());
      ResultSet set = checkUserStmt.executeQuery();
      if (set.next()) {
        set.close();
        return "User already exists\n";
      }
      set.close();
      byte[] sh = PasswordUtils.saltAndHashPassword(password);
      createStmt.clearParameters();
      createStmt.setString(1, username.toLowerCase());
      createStmt.setBytes(2, sh);
      createStmt.setInt(3, initAmount);
      createStmt.executeUpdate();
      return "Created user " + username + "\n";
    } catch (SQLException e) {
      return "Failed to create user\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // WARNING: the below code is insecure (it's susceptible to SQL injection attacks) AND only
    // handles searches for direct flights.  We are providing it *only* as an example of how
    // to use JDBC; you are required to replace it with your own secure implementation.
    //
    // TODO: YOUR CODE HERE

    try {
      recents.clear();
      List<Itinerary> it = new ArrayList<>();
      if (directFlight) {
          directStmt.clearParameters();
          directStmt.setString(1, originCity);
          directStmt.setString(2, destinationCity);
          directStmt.setInt(3, dayOfMonth);
          directStmt.setInt(4, numberOfItineraries);
          ResultSet set = directStmt.executeQuery();
          int id = 0;
          while (set.next()) {
            Flight f = new Flight(
              set.getInt("fid"),
              set.getInt("day_of_month"),
              set.getString("cid"),
              set.getString("op_carrier_flight_num"),
              set.getString("origin_city"),
              set.getString("dest_city"),
              set.getInt("duration_mins"),
              set.getInt("num_seats"),
              set.getInt("price")
            );
            it.add(new Itinerary(id++, Collections.singletonList(f)));
          }
          set.close();
      } else {
          List<Itinerary> direct = new ArrayList<>();
          List<Itinerary> indirect = new ArrayList<>();
            
          directStmt.clearParameters();
          directStmt.setString(1, originCity);
          directStmt.setString(2, destinationCity);
          directStmt.setInt(3, dayOfMonth);
          directStmt.setInt(4, numberOfItineraries);
          ResultSet dirSet = directStmt.executeQuery();
          int id = 0;
          while (dirSet.next()) {
              Flight f = new Flight(
              dirSet.getInt("fid"),
              dirSet.getInt("day_of_month"),
              dirSet.getString("cid"),
              dirSet.getString("op_carrier_flight_num"),
              dirSet.getString("origin_city"),
              dirSet.getString("dest_city"),
              dirSet.getInt("duration_mins"),
              dirSet.getInt("num_seats"),
              dirSet.getInt("price")
              );
              direct.add(new Itinerary(id++, Collections.singletonList(f)));
          }
          dirSet.close();
            
          if (direct.size() < numberOfItineraries) {
                indirectStmt.clearParameters();
                indirectStmt.setString(1, originCity);
                indirectStmt.setString(2, destinationCity);
                indirectStmt.setInt(3, dayOfMonth);
                indirectStmt.setInt(4, dayOfMonth);
                indirectStmt.setInt(5, numberOfItineraries - direct.size());
                
                ResultSet indirSet = indirectStmt.executeQuery();
                while (indirSet.next()) {
                    Flight f1 = new Flight(
                        indirSet.getInt("fid1"),
                        indirSet.getInt("day1"),
                        indirSet.getString("cid1"),
                        indirSet.getString("num1"),
                        indirSet.getString("origin1"),
                        indirSet.getString("stopover"),
                        indirSet.getInt("dur1"),
                        indirSet.getInt("cap1"),
                        indirSet.getInt("price1")
                    );
                    
                    Flight f2 = new Flight(
                        indirSet.getInt("fid2"),
                        indirSet.getInt("day2"),
                        indirSet.getString("cid2"),
                        indirSet.getString("num2"),
                        indirSet.getString("stopover"),
                        indirSet.getString("dest2"),
                        indirSet.getInt("dur2"),
                        indirSet.getInt("cap2"),
                        indirSet.getInt("price2")
                    );
                    
                    indirect.add(new Itinerary(id++, Arrays.asList(f1, f2)));
                }
                indirSet.close();
            }
            it.addAll(direct);
            it.addAll(indirect);
            if (it.size() > numberOfItineraries) {
                it = it.subList(0, numberOfItineraries);
            }
        }
        if (it.isEmpty()) {
            return "No results\n";
        }
        recents = it;
        StringBuilder sb = new StringBuilder();
        for (Itinerary i : it) {
            sb.append(i.toString());
        }
        return sb.toString();
    } catch (SQLException e) {
        return "Failed to search\n";
    }
  }

  class Itinerary {
    public int iid;
    public List<Flight> flights;
    public int totDuration;
    public int totPrice;

    public Itinerary(int itineraryId, List<Flight> flights) {
        this.iid = itineraryId;
        this.flights = flights;
        this.totDuration = flights.stream().mapToInt(f -> f.time).sum();
        this.totPrice = flights.stream().mapToInt(f -> f.price).sum();
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Itinerary ").append(iid).append(": ").append(flights.size()).append(" flight(s), ").append(totDuration).append(" minutes\n");
        for (Flight f : flights) {
            sb.append(f.toString()).append("\n");
        }
        
        return sb.toString();
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    // TODO: YOUR CODE HERE
    if (currentUser == null) return "Cannot book reservations, not logged in\n";
    if (itineraryId < 0 || itineraryId >= recents.size()) {
        return "No such itinerary " + itineraryId + "\n";
    }

    Itinerary it = recents.get(itineraryId);
    int flightDay = it.flights.get(0).dayOfMonth;

    try {
        conn.setAutoCommit(false);
        String checkSql = "SELECT COUNT(*) FROM Reservations_jisung r " +
                        "JOIN Itineraries_jisung i ON r.iid = i.iid " +
                        "WHERE r.username = ? AND i.day = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
        checkStmt.setString(1, currentUser);
        checkStmt.setInt(2, flightDay);
        ResultSet set = checkStmt.executeQuery();
        set.next();
        if (set.getInt(1) > 0) {
            conn.rollback();
            return "You cannot book two flights in the same day\n";
        }
        set.close();
        for (Flight f : it.flights) {
            if (!checkCapacity(f.fid)) {
                conn.rollback();
                return "Booking failed\n";
            }
        }

        String maxIidSql = "SELECT COALESCE(MAX(iid), 0) + 1 FROM Itineraries_jisung";
        Statement maxIidStmt = conn.createStatement();
        ResultSet iidRs = maxIidStmt.executeQuery(maxIidSql);
        iidRs.next();
        int iid = iidRs.getInt(1);
        iidRs.close();
        String insertItinerarySql = "INSERT INTO Itineraries_jisung (iid, day, price, duration, numFlights, fid1, fid2) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement itineraryStmt = conn.prepareStatement(insertItinerarySql);
        itineraryStmt.setInt(1, iid);
        itineraryStmt.setInt(2, flightDay);
        itineraryStmt.setInt(3, it.totPrice);
        itineraryStmt.setInt(4, it.totDuration);
        itineraryStmt.setInt(5, it.flights.size());
        itineraryStmt.setInt(6, it.flights.get(0).fid);
        if (it.flights.size() > 1) {
            itineraryStmt.setInt(7, it.flights.get(1).fid);
        } else {
            itineraryStmt.setNull(7, java.sql.Types.INTEGER);
        }
        itineraryStmt.executeUpdate();

        String maxRidSql = "SELECT COALESCE(MAX(rid), 0) + 1 FROM Reservations_jisung";
        Statement maxRidStmt = conn.createStatement();
        ResultSet ridRs = maxRidStmt.executeQuery(maxRidSql);
        ridRs.next();
        int rid = ridRs.getInt(1);
        ridRs.close();
        bookStmt.clearParameters();
        bookStmt.setInt(1, rid);
        bookStmt.setInt(2, 0);
        bookStmt.setString(3, currentUser);
        bookStmt.setInt(4, iid);
        bookStmt.executeUpdate();
        conn.commit();
        return "Booked flight(s), reservation ID: " + rid + "\n";
    } catch (SQLException e) {
        try { conn.rollback(); } catch (SQLException ex) {}
        return "Booking failed\n";
    } finally {
        try { conn.setAutoCommit(true); } catch (SQLException e) {}
    }
  }

  private boolean checkCapacity(int fid) throws SQLException { //??????????????
    // int cap = getFlightCapacity(fid);
    // String countSql = "SELECT COUNT(*) FROM Itineraries_jisung i " +
    //                  "JOIN Reservations_jisung r ON i.iid = r.iid " +
    //                  "WHERE i.fid1 = ? OR i.fid2 = ?";
    // PreparedStatement countStmt = conn.prepareStatement(countSql);
    // countStmt.setInt(1, fid);
    // countStmt.setInt(2, fid);
    // ResultSet set = countStmt.executeQuery();
    // set.next();
    // int count = set.getInt(1);
    // set.close();
    
    // return count < cap;
    int cap = getFlightCapacity(fid);
    capacityStmt.clearParameters();
    capacityStmt.setInt(1, fid);
    capacityStmt.setInt(2, fid);
    ResultSet set = capacityStmt.executeQuery();
    set.next();
    int count = set.getInt(1);
    set.close();
    return count < cap;
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    // TODO: YOUR CODE HERE
    if (currentUser == null) return "Cannot pay, not logged in\n";
    try {
        conn.setAutoCommit(false);

        String checkSql = "SELECT i.price, u.balance " +
                          "FROM Reservations_jisung r, Itineraries_jisung i, Users_jisung u " +
                          "WHERE r.rid = ? AND r.username = ? AND r.paid = 0 " +
                          "AND r.iid = i.iid AND r.username = u.username";
        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
        checkStmt.setInt(1, reservationId);
        checkStmt.setString(2, currentUser);
        ResultSet set = checkStmt.executeQuery();
        if (!set.next()) {
            conn.rollback();
            return "Cannot find unpaid reservation " + reservationId + " under user: " + currentUser + "\n";
        }
        
        int price = set.getInt("price");
        int balance = set.getInt("balance");
        set.close();
        if (balance < price) {
            conn.rollback();
            return "User has only " + balance + " in account but itinerary costs " + price + "\n";
        }

        updateBalanceStmt.clearParameters();
        updateBalanceStmt.setInt(1, balance - price);
        updateBalanceStmt.setString(2, currentUser);
        updateBalanceStmt.executeUpdate();
        payStmt.clearParameters();
        payStmt.setInt(1, reservationId);
        payStmt.setString(2, currentUser);
        payStmt.executeUpdate();
        conn.commit();
        return "Paid reservation: " + reservationId + " remaining balance: " + (balance - price) + "\n";
    } catch (SQLException e) {
        try { conn.rollback(); } catch (SQLException ex) {}
        return "Failed to pay for reservation " + reservationId + "\n";
    } finally {
        try { conn.setAutoCommit(true); } catch (SQLException e) {}
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    // TODO: YOUR CODE HERE
    if (currentUser == null) return "Cannot view reservations, not logged in\n";
    try {
        conn.setAutoCommit(false);
        StringBuilder sb = new StringBuilder();
        boolean exists = false;
        reservationsStmt.clearParameters();
        reservationsStmt.setString(1, currentUser);
        ResultSet set = reservationsStmt.executeQuery();
        while (set.next()) {
            exists = true;
            int rid = set.getInt("rid");
            int paid = set.getInt("paid");
            int fid1 = set.getInt("fid1");
            Integer fid2 = set.getObject("fid2") == null ? null : set.getInt("fid2");
            sb.append("Reservation ").append(rid).append(" paid: ").append(paid == 1 ? "true" : "false").append(":\n");
            Flight f1 = flightInfo(fid1);
            sb.append(f1.toString()).append("\n");
            if (fid2 != null) {
                Flight f2 = flightInfo(fid2);
                sb.append(f2.toString()).append("\n");
            }
        }
        set.close();
        if (!exists) {
            conn.commit();
            return "No reservations found\n";
        }
        conn.commit();
        return sb.toString();
    } catch (SQLException e) {
        try { conn.rollback(); } catch (SQLException ex) {}
        return "Failed to retrieve reservations\n";
    } finally {
        try { conn.setAutoCommit(true); } catch (SQLException e) {}
    }
  }

  // returns information about flight based on given flight id
  private Flight flightInfo(int fid) throws SQLException {// ??????????????????
    String s = "SELECT f.fid, f.day_of_month, f.cid, f.op_carrier_flight_num, f.origin_city, f.dest_city, f.duration_mins, a.num_seats, f.price " +
               "FROM Flights f, Aircraft_Types a, N_Numbers n " +
               "WHERE f.fid = ? AND f.tail_num = n.n_number AND n.mfr_mdl_code = a.atid";
    PreparedStatement st = conn.prepareStatement(s);
    st.setInt(1, fid);
    ResultSet set = st.executeQuery();
    if (!set.next()) {
      throw new SQLException("no such flight exists"); 
    }
    return new Flight(
      set.getInt("fid"),
      set.getInt("day_of_month"),
      set.getString("cid"),
      set.getString("op_carrier_flight_num"),
      set.getString("origin_city"),
      set.getString("dest_city"),
      set.getInt("duration_mins"),
      set.getInt("num_seats"),
      set.getInt("price")
    );
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int getFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("num_seats");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return "40001".equals(e.getSQLState()) || "40P01".equals(e.getSQLState());
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
