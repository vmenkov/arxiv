<html>
<head>
<title>My.Arxiv: search help</title>
</head>
<h1>My.Arxiv: search help</h1>

<p>
These are examples of queries you can type into the search box:
</p>

<dl>
<dt>rabbit
<dd>All occurrence of the word "rabbit"
<br>

<dt>"dying rabbit"
<dd>Phrasal query: All occurrence of the exact phrase "white rabbit". Double quotes are used for phrasal queries. Phrasal queries cannot be combined with anything else; i.e., if you use a double-quoted phrase, you can't have anything else in the search box besides it.

<dt>dying rabbit
<dd>The article  must include both "dying" and "rabbit", in any positions, in any field. Any number of terms can be listed together like this; it is an AND query. 

<dt>title:dying title:rabbit
<dd>The title of the article  must include both "dying" and "rabbit", in any positions. Any number of terms can be listed together like this; it is an AND query. 

<dt>authors:oller rabbit
<dd>The authors list must include Oller, and there must be the word "rabbit" in any field.

<dt>squirrel*
<dd>Prefix query: All occurrences of the words "squirrel", "squirrels", "squirreles", etc; "*" is a wild card. The "*" can only be used at the end of a word.

<dt>
forest squirrel*
<dd>The article  must include both "forest" and any word beginning with "squirrel". Wildcards can be used in any terms.

<dt>
tree category:cs.AI
<dd>
The  article  must include the word "tree", and its category (or one of its categories) must be "cs.AI"


<dt>
tree category:cs.*
<dd>
The  article  must include the word "tree", and its subject category  (or one of its categories) must begin with "cs."

<dt>
tree category:cs.* days:30
<dd>
The  article  must include the word "tree", and its category  (or one of its categories) must begin with "cs.", and it must have been added to ArXiv within the last 30 day.

<dt>authors:smith category:cs.*
<dd>"smith" must be in the author field, and the category must be in the cs.* tree.

<dt>authors:smith category:cs.*
<dd>"smith" must be in the author field, and the category must be in the cs.* tree.

</dl>

<p>The following prefixes are allowed: <strong>authors:  title: abstract: article: category: days:</strong>

<p>Query words are case-insensitive, <em>except</em> the <tt>category:xxx</tt> clauses.
<p>

</html>
