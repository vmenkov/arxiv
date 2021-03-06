<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.recommender.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewUserProfile main=new ViewUserProfile(request, response);
   User actor = main.actor;
%>

<html>
<head>
<meta name="Description" content="List of terms in the user profile, based on pages the user has been interested in"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Weight vector ("personal profil") for user <%= main.actorUserName %>
</title>
<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
</head>
<body>

<h1>
<% if (main.id>0) { %>
Profile no. <%=main.id%>
<% } else if (main.requestedFile!=null) { %>
Profile <%=main.requestedFile%>
<%}else{%>
Most recent currently available profile of the type <%=main.mode%>
<%}%>
for user
<em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {

     if (!main.force && main.upro!=null) {      
       long since = main.allOpCnt -main.reflectedOpCnt;
%>

<h4>Details of this profile</h4>

<p><%=main.thisUproTable %></p>

<p>User profile <%=main.df.getThisFile() %> was generated for user  
<%=main.df.getUser() %> at: <%=Util.ago(main.df.getTime())%>. 

<p>Profile generation method: <%=main.df.getType() %> 
(<%=main.df.getType().description() %>)

<p>At the time when this profile was generated, <%=
main.reflectedOpCnt %> operations had been recorded in the user's <a
href="<%=main.viewActionsLink()%>">activity log</a> (operation id thru
<%= main.df.getLastActionId() %>). The profile reflects some of
these operations. <small>(Note: most likely, only a fraction of 
these operations have been reflected in the profile. This is because only
operations carried out by users while vieweing most current suggestion list, and only on "learning days", are used for profile updating. Besides, user profiles were zeroed on 2014-01-24, which means that profiles generated after that date do not reflect earlier history).</small>

<p> There have been <%= since %> user activity operations recorded
since this profile was generated.

<% if (main.df.getType()== DataFile.Type.TJ_ALGO_2_USER_PROFILE && 
       main.ancestor!=null ) {
%>
<p>Algo 2: The profile has been generated by applying the  <%= main.df.getLastActionId() - main.ancestor.getLastActionId() %> user activity entries recorded since the suggestion list 
<%= main.formatSuggestionsLink(main.ancestor.getThisFile()) %>
had been generated.
</p>
<%}%>

<% if (main.requestedFile!=null) { %>

<p>This may or may not be the most recent profile. To see the most
recent profile, please <a
href="<%=main.viewLatestProfileLink()%>">click here</a>.

<% } else if (since>0) { %>

<!--
<p>(Is this too long ago? You can <a href="#tasks">update</a> the profile to account for your activity since this profile has been generated).
</p> -->

<% } else { %>

<!--
<p>(This profile appears to reflect all user actions recorded so far; nonetheless, if you really want to, you may try to request an <a href="#tasks">update</a> of the profile. This probably won't result in any visible changes to it.)
</p> -->

<% }  %>

<h4>Suggestion list(s) based on this user profile</h4>

<p>If any suggestion list(s) have been generated based on this user profile, they are listed in the table below. To view a suggestion list, click on its id (in the first column of the table).

<p>
<%=main.sugTable%>
</p>

<!--
<p>Want to see a suggestion list based on this profile? Use the form below:
<form action="viewSuggestions.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.BASEDON%>" value="<%=main.df.getThisFile() %>">
<input type="hidden" name="<%=main.EXPERT%>" value="<%=main.expert %>">
<input type="radio" name="<%=main.MODE%>" 
value="<%=DataFile.Type.LINEAR_SUGGESTIONS_1%>"> - linear similarity
<br>
<input type="radio" name="<%=main.MODE%>" 
value="<%=DataFile.Type.TJ_ALGO_1_SUGGESTIONS_1%>"> - sublinear utility (Algo 1)
<br>
<input type="radio" name="<%=main.MODE%>" 
value="<%=DataFile.Type.PPP_SUGGESTIONS%>" checked> - 3PR suggestions
<br>
Apply Most recent <input type="text" name="<%=main.DAYS%>" value="<%=actor.getDays()%>"> days. (Enter a positive number for a desired range, or 0 for "all time"). 
<br>
<input type="submit" value="View suggestions">
</form>
</p>

-->

<h4>Parent profile</h4>

<p>If this user profile has been created based on an older profile, information about the latter should be shown below.</p>

<p><%=main.prevUproTable %></p>

<h4>Derived user profile(s)</h4>

<P>If this user profile is not the most recent one, the following table will show the profile(s) created based on it. Click on the "id" link (in the first column) for more information on the profile.</p>

<p><%=main.nextUproTable %></p>

<h4>Profile content</h4>

<p>The user profile vector contains <%=main.upro.terms.length%> terms. They are listed below in the descending order of f(t)*idf(t).
</p>

<p>
<table>
<tr>
<th>No.</th>
<th>Term <em>t</em></th>
<th>f(<em>t</em>)</th>
<th>f(SQR(<em>t</em>))</th>
<th>idf(<em>t</em>)</th>
</tr>

<% for(int i=0; i<main.upro.terms.length; i++) { 
String t=main.upro.terms[i];
UserProfile.TwoVal h= main.upro.hq.get(t); 
%>
<tr>
<td><%=i%></td>
<td><%=t%></td>
<td><%=h.w1%></td>
<td><%=h.w2%></td>
<td><%=main.upro.idf(t)%></td>
</tr>

<% if (!main.showAll && i>=100) {%>
   <tr><td colspan=5>
         (The list is truncated. You can <a href="<%=main.selfUrlPlus("all=true")%>">see the entire profile</a>.)
   </td></tr>
<%    break;
   }
}%>
</table>
</p>


<% } else if (main.requestedFile!=null)  { %>
<p>No profile file found with this name: <%=main.requestedFile%>
<% } else if (!main.force)  { %>

<P>No user profile with the requested characteristcs has yet been
generated for user <em><%= main.actorUserName %></em>.</p>

<% } %>

<%if (main.requestedFile==null &&  main.mode!=DataFile.Type.PPP_USER_PROFILE){%>
<h2><a name="tasks">(Re-)computing the profile</a></h2>

<%    if (main.activeTask!=null) {
%> 
<p>
Presently, a task is running to generate the user profile: <%= main.activeTask%>. You may want to wait for a couple of minutes, and then reload this page to see if it has completed.</p>
<%      
      } else if (main.queuedTask!=null) {
%>
<p>
Presently, a task is queued to generate the user profile: <%= main.queuedTask%>. You may wait for a few minutes, and then refresh this page to see if it has started and completed.</p>
 <%
       } else if (main.newTask!=null) {
%>
<p>
A new task to generate the user profile has just been created and queued: <%= main.newTask%>. You may wait for a couple minutes, and then reload this page to see if it has started and completed.</p>
 <%
     }   else {
%>
Presently, no task has been scheduled to (re-)generate the user profile. If desired, you can request it now.
</p>
 <%      
      }
%>

<% if (main.newTask==null) { %>
<p>
Due to the computational costs, user profiles are created in an off-line mode. 
To create a new computational task to <%= (main.df==null) ? "create":"update" %>
the user profile for <em><%= main.actorUserName %></em>, click on the button below:
<form action="viewUserProfile.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.FORCE%>" value="true">
<input type="hidden" name="<%=main.MODE%>" value="<%=main.mode%>">
<input type="submit" value="Create task">
</form>
</p>
<%  }  
}%>

<hr>
<p>
<form action="viewUserProfile.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.MODE%>" value="<%=main.mode%>">
<input type="submit" value="Refresh view">
</form>
</p>

<hr>
<h4>Finding other user profiles</h4>

<p>You can use the following JPQL query to list all of your recent user profiles.

<p>
<form action="../tools/QueryServlet">
<textarea name="query" rows=2 cols=120>
select f from DataFile f where f.time > '2013-12-01' and f.user = '<%=main.actorUserName%>'
and f.type = edu.rutgers.axs.sql.DataFile$Type.<%=main.mode%> order by f.id desc
</textarea>

<input type="hidden" name="language" value="JPQL"/>
<input type="hidden" name="format" value="HTML"/>
<input type="hidden" name="header" value="true"/>
<br>
<input type="submit" value="List user profiles">
</form>
</p>


<%  }  %>


<hr> <p><small>System message: <%= main.infomsg%> </small></p> <%  %>

<icd:RU/>

</body>
</html>