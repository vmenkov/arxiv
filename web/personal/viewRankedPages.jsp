<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewRankedPages main=new ViewRankedPages(request, response, true);
   User actor = main.actor;
%>

<html>
<head>
<meta name="Description" content="List of articles viewed/rated/saved/judged by the user, ranked by their putative importance to the user"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Personal folder for user <%= main.actorUserName %>
</title>
<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
</head>
<body>

<h1>Personal ranked list of viewed articles for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>


<P>The list below includes all articles that you have viewed, rated,
or copied to your personal folder.  This order is meant to rank
articles with respect to their usefulness/importance for you, as
inferred by the my.arxiv.org based on the history of your interaction
with this article on this site.

<p> The articles in this list are rated in the order of descending
"score", which is computed based on the history of your interaction
with each article. In particular:
<ul>
<li> articles that you have in your folder are ranked above all other
articles;

<li>explicit ratings you have supplied result in a smaller
contribution to the score;

<li>finally, viewing the article's body or abstract contributes an
even smaller amount to the overall score as well.

<li> With all actions on two articles being identical, the one with
the more recent action appears highers in the list.
</ul> </p>

<p>You cannot directly control the position of articles in this list,
but you can affect it by copying the article into your personal folder
or removing it from it, by rating an article in the search results (or
clicking on the "don't show it again" button), or by viewing it.

<p>This ranking will be used by my.arxiv.org to infer your research
interests in order to generate personalized recommendations.

<p>There are <%=main.ups.length %> articles on the list.
</P>

<table border=1>
<tr>
<th>No. / Score
<th>Article
<th>Actions that have<br> contributed<br> to the score
</tr>

<%	
	for(int i=0; i<main.ups.length; i++) {
	UserPageScore u=main.ups[i];
	ArticleEntry e = main.entries.elementAt(i);
%>
	<tr id="result<%=i%>">
	<td><%=(i+1)%>. <br> <%= u.score %>
	<td>
	<% if (e!=null) { %>
	 <%= e.idline %> 
	 [<a href ="<%=main.urlAbstract(e.id)%>">Abstract</a>]
	 [<a href="<%=main.urlPDF(e.id)%>">PDF/PS/etc</a>] <br>
	<%= e.titline %><br />
			<%= e.authline %><br>
	<% if(!e.commline.equals("")) { %>	<%= e.commline %><br> <%}%>
			<%= e.subjline %><br>
	
	<% } else { %>
	   <%=u.getArticle()%>
	<% }  %>
	<td>
	<% for(Action a: u.reasons) { %>
	<%= a.getOp() %><br>
	<% }  %>
	</tr>
 <%  }  %>
</table></p>


<hr> <p><small>System message: <%= main.infomsg%> </small></p> <% } %>

<icd:RU/>

</body>
</html>