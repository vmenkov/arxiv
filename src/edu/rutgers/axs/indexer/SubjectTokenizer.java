package edu.rutgers.axs.indexer;


import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;


public final class SubjectTokenizer extends Tokenizer {
 
  /**
   * 
   * @param _text Text to tokenize
   *
   * 
   */
  public SubjectTokenizer(String _text) {
      super();
      text  = _text;
      words = text.trim().split("\\s+");
  }

  // this tokenizer generates three attributes:
  // term offset, positionIncrement and type
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private String text;
    private int offset=0, nextOffset=0;
    private String[] words;
    private int currentToken = -1;

  /*
   * (non-Javadoc)
   *
   * @see org.apache.lucene.analysis.TokenStream#next()
   */
  @Override
  public final boolean incrementToken() throws IOException {
    clearAttributes();
    int posIncr = 1;

    currentToken++;

    if (currentToken >= words.length) {
        return false;
    } else {
        posIncrAtt.setPositionIncrement(posIncr);
	String token = words[currentToken];
	offset = text.indexOf(token, nextOffset);
	nextOffset = offset + token.length();

	offsetAtt.setOffset(offset, nextOffset);
	termAtt.setEmpty().append(token);
	typeAtt.setType("word");

        return true;
    }
  }
  
  @Override
  public final void end() {
      offsetAtt.setOffset(text.length(), text.length());
  }

  @Override
  public void reset(Reader reader) throws IOException {
    super.reset(reader);
    offset= nextOffset=0;
    currentToken = -1;
  }

 
}
