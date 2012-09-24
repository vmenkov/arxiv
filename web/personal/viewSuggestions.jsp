<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>

<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.recommender.*" %>
<%@ page import="edu.rutgers.axs.html.*" %>

<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	ViewSuggestions main=new ViewSuggestions(request,response);
	SearchResults sr = main.sr;
%>
<!-- © 2011 by AEP -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>My.arXiv - Search results</title>
<!-- #EndEditable -->
<meta name="Keywords" content="" />
<meta name="Description" content="Suggestion list generated by My.Arxiv" />
<meta name="Author" content="pichugin@eden.rutgers.edu" />
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />

<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_ie.css" /><![endif]-->
<link rel="stylesheet" type="text/css" href="../styles/results.css" />
<!--[if IE]>
<link rel="stylesheet" type="text/css" href="../styles/results_ie.css" />
<![endif]-->
<link rel="icon" type="image/x-icon" href="../favicon.ico" />

<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="../scripts/blur.js"></script>
<script type="text/javascript" src="../scripts/buttons_control.js"></script>

<!-- #EndEditable -->

</head>

<body>


<div id="big_middle_frame">
		<div id="wrapping"> 
		<!-- #BeginEditable "Body" -->	


<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {     %>
<%
     if (!main.force && sr !=null && sr.entries!=null) {
       long since = main.actor.getLastActionId() -main.df.getLastActionId();
 %>

<% if (main.teamDraft) {%>
		<h1>Suggestions - merged list</h1>

<em>The list presented here has been obtained by team-draft merger of the user category search results (Treatment A), and the Algo 1 suggestion list (Treatment B) as outlined below.</em>

<h2>Underlying Treatment B suggestion list description</h2>

<% } else { %>
		<h1>Suggestions</h1>

<% }%>

<p>Suggestion list <%=main.df.getThisFile() %> was generated for user
<%=main.df.getUser() %> at: <%=Util.ago(main.df.getTime())%>.

<p>The list was generated by applying the user profile 
<a href="<%=main.viewProfileLink(main.df.getInputFile())%>"><%=main.df.getInputFile()%></a>
to the 
<%= (main.days>0)? "articles from the last " + main.days + " days" :
    "entire article archive (all times)" %>. 
		
<p>This profile reflects <%= main.df.getLastActionId() %> operations recorded in the user's 
<a href="<%=main.viewActionsLink()%>">activity log</a>. There have been <%= since %> user activity operations recorded since.

<% if (main.expert && since>0) { %>

<p>(Was this too long ago? 
You can <a href="#tasks">update</a> the suggestion list to
account for your activity and new article submissions since this
list has been generated).  </p>

<% } else if (main.expert) { %>

<p>(This list appears to reflect all user actions recorded so far; nonetheless, if you really want to, you can probably request an <a href="#tasks">update</a> of the suggestion list. This may also reflect new articles added to the database since the last suggestion list update.)
</p>

<% }  %>


<p>Suggestion generation method: <%=main.df.getType() %>
 ( <%=main.df.getType().description() %>)

<p>The entire list contains at least <%= sr.reportedLength %>
articles; articles ranked from <%= sr.entries.elementAt(0).i %>
through <%= sr.entries.lastElement().i %> are shown below.

</p>
		<% 
for( ArticleEntry e: sr.entries) { %>
<%= main.resultsDivHTML(e) %>	
		<% }%>

	 <!-- Links to prev/next pages, if necessary -->

	<% if (sr.needPrev) {	%> 
	<a href="<%= main.repageUrl(sr.prevstart) %>">[PREV PAGE]</a> 
	<% }	
	 if (sr.needNext) { %>	
	<a href="<%= main.repageUrl(sr.nextstart) %>">[NEXT PAGE]</a> 	 
	<% }	 %>
		
		<% if (sr.excludedEntries.size()>0) { %>
		<div><small>We have excluded <%=sr.excludedEntries.size()%> articles from the list, because you have earlier asked not to show them anymore.</small></div>
		<% } %>
	

<% } else if (!main.force) { %>
<P>No suggestion list of the specified type for user <em><%= main.actorUserName %></em> has been generated yet. 
</p>
<% } %>
	
<% if (main.expert) { %>

<h2><a name="tasks">(Re-)computing the suggestion list</a></h2>

<%    if (main.activeTask!=null) {
%> 
<p>
Presently, a task is running to generate the suggestion list: <%= main.activeTask%>. You may want to wait for a couple of minutes, and then refresh this page to see if it has completed.</p>
<%      
      } else if (main.queuedTask!=null) {
%>
<p>
Presently, a task is queued to generate the suggestion list: <%= main.queuedTask%>. You may wait for a few minutes, and then refresh this page to see if it has started and completed.</p>
 <%
       } else if (main.newTask!=null) {
%>
<p>
A new task to generate the suggestion list has just been created and queued: <%= main.newTask%>. You may wait for a couple minutes, and then refresh this page to see if it has started and completed.</p>
 <%
     }   else {
%>
Presently, no task has been scheduled to (re-)generate the suggestion list. If desired, you can request it now.
</p>
 <%      
      }
%>

<% if (main.newTask==null) { %>

<p>
<span id="recompute">
Due to the computational costs, suggestion lists are created in an off-line mode. 
To create a new computational task to <%= (main.df==null) ? "create":"update" %>
the suggestion list for <em><%= main.actorUserName %></em>, click on the button below:
<form action="viewSuggestions.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.FORCE%>" value="true">
<input type="hidden" name="<%=main.MODE%>" value="<%=main.mode%>">
Most recent <input type="text" name="<%=main.DAYS%>" value="<%=main.days%>"> days. (Enter a positive number for a desired range, or 0 for "all time").
<input type="submit" value="Create task">
</form>
</p>

<p>Note: presently, creating a task here will merely apply the existing user profile to the current dataset. If you actually want to see suggestions generated with an updated profile, you have to explicitly update the profile (of the appropriate type) first!</p>

<%  }  %>

<hr>
<p>
<form action="viewSuggestions.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.MODE%>" value="<%=main.mode%>">
<input type="hidden" name="<%=main.DAYS%>" value="<%=main.days%>">
<% if (main.basedon!=null) { %>
<input type="hidden" name="<%=main.BASEDON%>" value="<%=main.basedon%>">
<% } %>
<a name="refresh"><input type="submit" value="Refresh view"></a>
</form>
</p>

<%  } }  %>

<hr> <p><small>System message: <%= main.infomsg%> </small></p> <%  %>

<icd:RU/>



		<!-- #EndEditable -->	
	</div> <!-- Wrapping -->
</div> <!-- middle frame -->


</body>

</html>
