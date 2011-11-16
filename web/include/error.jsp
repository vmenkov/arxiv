<h3>Error</h3>
<p> <em class="errMsg"><%= main.errmsg %></em></p>
<% if (main.e != null && main.e instanceof edu.rutgers.axs.sql.IllegalInputException) { %>
<p>
   Illegal input:<br>
<%=  main.e.getMessage() %> <br>
Please go back, correct the appropriate field, and resubmit.
</p>
<% } else if (main.e != null) { %>
<p><%= main.e %></p>
<hr>
<p>Details of the exception, if you care for them:</p>

<p><small>
<pre><%= main.exceptionTrace() %></pre></small></p>
<%      } %>
