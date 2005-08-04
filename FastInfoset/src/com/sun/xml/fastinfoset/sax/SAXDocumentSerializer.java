/*
 * Fast Infoset ver. 0.1 software ("Software")
 *
 * Copyright, 2004-2005 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Software is licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at:
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations.
 *
 *    Sun supports and benefits from the global community of open source
 * developers, and thanks the community for its important contributions and
 * open standards-based technology, which Sun has adopted into many of its
 * products.
 *
 *    Please note that portions of Software may be provided with notices and
 * open source licenses from such communities and third parties that govern the
 * use of those portions, and any licenses granted hereunder do not alter any
 * rights and obligations you may have under such open source licenses,
 * however, the disclaimer of warranty and limitation of liability provisions
 * in this License will apply to all Software in this distribution.
 *
 *    You acknowledge that the Software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any nuclear
 * facility.
 *
 * Apache License
 * Version 2.0, January 2004
 * http://www.apache.org/licenses/
 *
 */


package com.sun.xml.fastinfoset.sax;

import com.sun.xml.fastinfoset.Encoder;
import com.sun.xml.fastinfoset.EncodingConstants;
import com.sun.xml.fastinfoset.QualifiedName;
import org.jvnet.fastinfoset.sax.FastInfosetWriter;
import com.sun.xml.fastinfoset.util.LocalNameQualifiedNamesMap;
import java.io.IOException;
import org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import org.jvnet.fastinfoset.FastInfosetException;
import org.jvnet.fastinfoset.RestrictedAlphabet;
import org.jvnet.fastinfoset.sax.EncodingAlgorithmAttributes;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import com.sun.xml.fastinfoset.CommonResourceBundle;


public class SAXDocumentSerializer extends Encoder implements FastInfosetWriter {
    protected boolean _elementHasNamespaces = false;

    protected boolean _charactersAsCDATA = false;
    
    public SAXDocumentSerializer() {
    }


    public void reset() {
        super.reset();
        
        _elementHasNamespaces = false;
        _charactersAsCDATA = false;
    }
    
    // ContentHandler

    public final void startDocument() throws SAXException {
        try {
            reset();
            encodeHeader(false);
            encodeInitialVocabulary();
        } catch (IOException e) {
            throw new SAXException("startDocument", e);
        }
    }

    public final void endDocument() throws SAXException {
        try {
            encodeDocumentTermination();
        } catch (IOException e) {
            throw new SAXException("endDocument", e);
        }
    }

    public final void startPrefixMapping(String prefix, String uri) throws SAXException {
        try {
            if (_elementHasNamespaces == false) {
                encodeTermination();

                // Mark the current buffer position to flag attributes if necessary
                mark();
                _elementHasNamespaces = true;

                // Write out Element byte with namespaces
                write(EncodingConstants.ELEMENT | EncodingConstants.ELEMENT_NAMESPACES_FLAG);
            }

            encodeNamespaceAttribute(prefix, uri);
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        // TODO consider using buffer for encoding of attributes, then pre-counting is not necessary
        final int attributeCount = (atts.getLength() > 0) ? countAttributes(atts) : 0;
        try {
            if (_elementHasNamespaces) {
                _elementHasNamespaces = false;

                if (attributeCount > 0) {
                    // Flag the marked byte with attributes
                    _octetBuffer[_markIndex] |= EncodingConstants.ELEMENT_ATTRIBUTE_FLAG;
                }
                resetMark();

                write(EncodingConstants.TERMINATOR);

                _b = 0;
            } else {
                encodeTermination();

                _b = EncodingConstants.ELEMENT;
                if (attributeCount > 0) {
                    _b |= EncodingConstants.ELEMENT_ATTRIBUTE_FLAG;
                }
            }

            encodeElement(namespaceURI, qName, localName);

            if (attributeCount > 0) {
                boolean addToTable;
                String value;
                if (atts instanceof EncodingAlgorithmAttributes) {
                    final EncodingAlgorithmAttributes eAtts = (EncodingAlgorithmAttributes)atts;
                    for (int i = 0; i < eAtts.getLength(); i++) {
                        if (encodeAttribute(atts.getURI(i), atts.getQName(i), atts.getLocalName(i))) {
                            value = eAtts.getValue(i);
                            if (value != null) {
                                addToTable = (value.length() < attributeValueSizeConstraint) ? true : false;
                                encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
                            } else {
                                encodeNonIdentifyingStringOnFirstBit(eAtts.getAlgorithmURI(i),
                                        eAtts.getAlgorithmIndex(i), eAtts.getAlgorithmData(i));
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < atts.getLength(); i++) {
                        if (encodeAttribute(atts.getURI(i), atts.getQName(i), atts.getLocalName(i))) {
                            value = atts.getValue(i);
                            addToTable = (value.length() < attributeValueSizeConstraint) ? true : false;
                            encodeNonIdentifyingStringOnFirstBit(value, _v.attributeValue, addToTable);
                        }
                    }
                }
                _b = EncodingConstants.TERMINATOR;
                _terminate = true;
            }
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        } catch (FastInfosetException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final int countAttributes(Attributes atts) {
        // Count attributes ignoring any in the XMLNS namespace
        // Note, such attributes may be produced when transforming from a DOM node
        int count = 0;
        for (int i = 0; i < atts.getLength(); i++) {
            final String uri = atts.getURI(i);
            if (uri == "http://www.w3.org/2000/xmlns/" || uri.equals("http://www.w3.org/2000/xmlns/")) {
                continue;
            }
            count++;
        }
        return count;
    }

    public final void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        try {
            encodeElementTermination();
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final void characters(char[] ch, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            if (!_charactersAsCDATA) {
                encodeCharacters(ch, start, length);
            } else {
                encodeCIIBuiltInAlgorithmDataAsCDATA(ch, start, length);
            }
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        characters(ch, start, length);
    }

    public final void processingInstruction(String target, String data) throws SAXException {
        try {
            if (target == "") {
                throw new SAXException(CommonResourceBundle.getInstance().getString("message.processingInstructionTargetIsEmpty"));
            }
            encodeTermination();

            encodeProcessingInstruction(target, data);
        } catch (IOException e) {
            throw new SAXException("processingInstruction", e);
        }
    }

    public final void setDocumentLocator(org.xml.sax.Locator locator) {
    }

    public final void skippedEntity(String name) throws SAXException {
    }



    // LexicalHandler

    public final void comment(char[] ch, int start, int length) throws SAXException {
        try {
            encodeTermination();

            encodeComment(ch, start, length);
        } catch (IOException e) {
            throw new SAXException("startElement", e);
        }
    }

    public final void startCDATA() throws SAXException {
        _charactersAsCDATA = true;
    }

    public final void endCDATA() throws SAXException {
        _charactersAsCDATA = false;
    }

    public final void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public final void endDTD() throws SAXException {
    }

    public final void startEntity(String name) throws SAXException {
    }

    public final void endEntity(String name) throws SAXException {
    }


    // EncodingAlgorithmContentHandler

    public final void octets(String URI, int id, byte[] b, int start, int length)  throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeNonIdentifyingStringOnThirdBit(URI, id, b, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void object(String URI, int id, Object data)  throws SAXException {
        try {
            encodeTermination();

            encodeNonIdentifyingStringOnThirdBit(URI, id, data);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }


    // PrimitiveTypeContentHandler

    public final void bytes(byte[] b, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIOctetAlgorithmData(EncodingAlgorithmIndexes.BASE64, b, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    public final void shorts(short[] s, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.SHORT, s, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void ints(int[] i, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.INT, i, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void longs(long[] l, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.LONG, l, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void booleans(boolean[] b, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.BOOLEAN, b, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }
    
    public final void floats(float[] f, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.FLOAT, f, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public final void doubles(double[] d, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.DOUBLE, d, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }

    public void uuids(long[] msblsb, int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeCIIBuiltInAlgorithmData(EncodingAlgorithmIndexes.UUID, msblsb, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }


    // RestrictedAlphabetContentHandler
    
    public void numericCharacters(char ch[], int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeFourBitCharacters(RestrictedAlphabet.NUMERIC_CHARACTERS_INDEX, EncodingConstants.NUMERIC_CHARACTERS_TABLE, ch, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }
    
    public void dateTimeCharacters(char ch[], int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeFourBitCharacters(RestrictedAlphabet.DATE_TIME_CHARACTERS_INDEX, EncodingConstants.DATE_TIME_CHARACTERS_TABLE, ch, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }
    
    public void alphabetCharacters(String alphabet, char ch[], int start, int length) throws SAXException {
        if (length <= 0) {
            return;
        }

        try {
            encodeTermination();

            encodeAlphabetCharacters(alphabet, ch, start, length);
        } catch (IOException e) {
            throw new SAXException(e);
        } catch (FastInfosetException e) {
            throw new SAXException(e);
        }
    }



    protected final void encodeElement(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.elementName.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnThirdBit(names[i].index);
                    return;
                }
            }
        }

        encodeLiteralElementQualifiedNameOnThirdBit(namespaceURI, getPrefixFromQualifiedName(qName),
                localName, entry);
    }

    protected final boolean encodeAttribute(String namespaceURI, String qName, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.attributeName.obtainEntry(qName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnSecondBitFirstBitZero(names[i].index);
                    return true;
                }
            }
        }

        return encodeLiteralAttributeQualifiedNameOnSecondBit(namespaceURI, getPrefixFromQualifiedName(qName),
                localName, entry);
    }
}
