/*
 * Copyright 2012, 2014 the original author or authors.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.nubits.nubitsj.uri;

import static com.google.common.base.Preconditions.checkNotNull;
import com.nubits.nubitsj.core.Address;
import com.nubits.nubitsj.core.AddressFormatException;
import com.nubits.nubitsj.core.Coin;
import com.nubits.nubitsj.core.Monetary;
import com.nubits.nubitsj.core.NetworkParameters;
import com.nubits.nubitsj.params.MainNetParams;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Provides a standard implementation of a Nubits URI with support for the following:</p>
 *
 * <ul>
 * <li>URLEncoded URIs (as passed in by IE on the command line)</li>
 * <li>BIP21 names (including the "req-" prefix handling requirements)</li>
 * </ul>
 *
 * <h2>Accepted formats</h2>
 *
 * <p>The following input forms are accepted:</p>
 *
 * <ul>
 * <li>{@code nubits:<address>}</li>
 * <li>{@code nubits:<address>?<name1>=<value1>&<name2>=<value2>} with multiple
 * additional name/value pairs</li>
 * </ul>
 *
 * <p>The name/value pairs are processed as follows.</p>
 * <ol>
 * <li>URL encoding is stripped and treated as UTF-8</li>
 * <li>names prefixed with {@code req-} are treated as required and if unknown or conflicting cause a parse exception</li>
 * <li>Unknown names not prefixed with {@code req-} are added to a Map, accessible by parameter name</li>
 * <li>Known names not prefixed with {@code req-} are processed unless they are malformed</li>
 * </ol>
 *
 * <p>The following names are known and have the following formats:</p>
 * <ul>
 * <li>{@code amount} decimal value to 8 dp (e.g. 0.12345678) <b>Note that the
 * exponent notation is not supported any more</b></li>
 * <li>{@code label} any URL encoded alphanumeric</li>
 * <li>{@code message} any URL encoded alphanumeric</li>
 * </ul>
 * 
 * @author Andreas Schildbach (initial code)
 * @author Jim Burton (enhancements for MultiBit)
 * @author Gary Rowe (BIP21 support)
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki">BIP 0021</a>
 */
public class NubitsURI {
    /**
     * Provides logging for this class
     */
    private static final Logger log = LoggerFactory.getLogger(NubitsURI.class);

    // Not worth turning into an enum
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_LABEL = "label";
    public static final String FIELD_AMOUNT = "amount";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_PAYMENT_REQUEST_URL = "r";

    public static final String Nubits_SCHEME = "Nu";
    private static final String ENCODED_SPACE_CHARACTER = "%20";
    private static final String AMPERSAND_SEPARATOR = "&";
    private static final String QUESTION_MARK_SEPARATOR = "?";

    /**
     * Contains all the parameters in the order in which they were processed
     */
    private final Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();

    /**
     * Constructs a new NubitsURI from the given string for the NuBits main network.
     *
     * @param uri The raw URI data to be parsed (see class comments for accepted formats)
     * @throws NubitsURIParseException if the URI is not syntactically or semantically valid.
     */
    public NubitsURI(String uri) throws NubitsURIParseException {
        this(MainNetParams.get(), uri);
    }

    /**
     * Given a particular prefix, parse the input string to create a NubitsURI object
     *
     * @params params The network parameters that determine which network the URI is from, or null if you don't have
     *                any expectation about what network the URI is for and wish to check yourself.
     * @params input The raw URI data to be parsed (see class comments for accepted formats)
     * @params prefix The coin prefix to check for
     *
     * @throws NubitsURIParseException If the input fails Nubits URI syntax and semantic checks for the specified
     * network.
     */
    public NubitsURI(NetworkParameters params, String input, String prefix) throws NubitsURIParseException  {
        checkNotNull(input);
        log.debug("Attempting to parse '{}' for {}", input, params == null ? "any" : params.getId());

        // Attempt to form the URI (fail fast syntax checking to official standards).
        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException e) {
            throw new NubitsURIParseException("Bad URI syntax", e);
        }

        // URI is formed as  nubits:<address>?<query parameters>
        // blockchain.info generates URIs of non-BIP compliant form nubits://address?....
        // We support both until Ben fixes his code.

        // Remove the nubits scheme.
        // (Note: getSchemeSpecificPart() is not used as it unescapes the label and parse then fails.
        // For instance with : nubits:129mVqKUmJ9uwPxKJBnNdABbuaaNfho4Ha?amount=0.06&label=Tom%20%26%20Jerry
        // the & (%26) in Tom and Jerry gets interpreted as a separator and the label then gets parsed
        // as 'Tom ' instead of 'Tom & Jerry')
        String schemeSpecificPart;
        if (input.startsWith(prefix + "://")) {
            schemeSpecificPart = input.substring((prefix + "://").length());
        } else if (input.startsWith(prefix + ":")) {
            schemeSpecificPart = input.substring((prefix + ":").length());
        } else {
            throw new NubitsURIParseException("Unsupported URI scheme: " + uri.getScheme());
        }

        // Split off the address from the rest of the query parameters.
        String[] addressSplitTokens = schemeSpecificPart.split("\\?", 2);
        if (addressSplitTokens.length == 0)
            throw new NubitsURIParseException("No data found after the " + prefix + ": prefix");
        String addressToken = addressSplitTokens[0];  // may be empty!

        String[] nameValuePairTokens;
        if (addressSplitTokens.length == 1) {
            // Only an address is specified - use an empty '<name>=<value>' token array.
            nameValuePairTokens = new String[] {};
        } else {
            // Split into '<name>=<value>' tokens.
            nameValuePairTokens = addressSplitTokens[1].split("&");
        }

        // Attempt to parse the rest of the URI parameters.
        parseParameters(params, addressToken, nameValuePairTokens);

        if (!addressToken.isEmpty()) {
            // Attempt to parse the addressToken as a Nubits address for this network
            try {
                Address address = new Address(params, addressToken);
                putWithValidation(FIELD_ADDRESS, address);
            } catch (final AddressFormatException e) {
                throw new NubitsURIParseException("Bad address", e);
            }
        }

        if (addressToken.isEmpty() && getPaymentRequestUrl() == null) {
            throw new NubitsURIParseException("No address and no r= parameter found");
        }
    }

    // Needed due to stupid Java restriction of including statements before a call to "this"
    static private String prepareNubitsURIInput(String input) {

        // Allow nu
        if (input.startsWith("nu")) {
            StringBuilder sb = new StringBuilder(input);
            sb.setCharAt(0, 'N');
            input = sb.toString();
        }

        return input;

    }

    /**
     * Constructs a new object by trying to parse the input as a valid Nubits URI.
     *
     * @param params The network parameters that determine which network the URI is from.
     * @param input The raw URI data to be parsed (see class comments for accepted formats)
     *
     * @throws NubitsURIParseException If the input fails Nubits URI syntax and semantic checks.
     */
    public NubitsURI(NetworkParameters params, String input) throws NubitsURIParseException {
        this(params, prepareNubitsURIInput(input), Nubits_SCHEME);
    }

    /**
     * @param params The network parameters or null
     * @param nameValuePairTokens The tokens representing the name value pairs (assumed to be
     *                            separated by '=' e.g. 'amount=0.2')
     */
    private void parseParameters(@Nullable NetworkParameters params, String addressToken, String[] nameValuePairTokens) throws NubitsURIParseException {
        // Attempt to decode the rest of the tokens into a parameter map.
        for (String nameValuePairToken : nameValuePairTokens) {
            final int sepIndex = nameValuePairToken.indexOf('=');
            if (sepIndex == -1)
                throw new NubitsURIParseException("Malformed Nubits URI - no separator in '" +
                        nameValuePairToken + "'");
            if (sepIndex == 0)
                throw new NubitsURIParseException("Malformed Nubits URI - empty name '" +
                        nameValuePairToken + "'");
            final String nameToken = nameValuePairToken.substring(0, sepIndex).toLowerCase(Locale.ENGLISH);
            final String valueToken = nameValuePairToken.substring(sepIndex + 1);

            // Parse the amount.
            if (FIELD_AMOUNT.equals(nameToken)) {
                // Decode the amount (contains an optional decimal component to 4dp).
                try {
                    Monetary amount = params.parseCoin(valueToken);
                    if (amount.signum() < 0)
                        throw new ArithmeticException("Negative coins specified");
                    putWithValidation(FIELD_AMOUNT, amount);
                } catch (IllegalArgumentException e) {
                    throw new OptionalFieldValidationException(String.format("'%s' is not a valid amount", valueToken), e);
                } catch (ArithmeticException e) {
                    throw new OptionalFieldValidationException(String.format("'%s' has too many decimal places", valueToken), e);
                }
            } else {
                if (nameToken.startsWith("req-")) {
                    // A required parameter that we do not know about.
                    throw new RequiredFieldValidationException("'" + nameToken + "' is required but not known, this URI is not valid");
                } else {
                    // Known fields and unknown parameters that are optional.
                    try {
                        if (valueToken.length() > 0 && (!params.isShapeShift() || !nameToken.equals(FIELD_PAYMENT_REQUEST_URL)))
                            putWithValidation(nameToken, URLDecoder.decode(valueToken, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        // Unreachable.
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        // Note to the future: when you want to implement 'req-expires' have a look at commit 410a53791841
        // which had it in.
    }

    /**
     * Put the value against the key in the map checking for duplication. This avoids address field overwrite etc.
     * 
     * @param key The key for the map
     * @param value The value to store
     */
    private void putWithValidation(String key, Object value) throws NubitsURIParseException {
        if (parameterMap.containsKey(key)) {
            throw new NubitsURIParseException(String.format("'%s' is duplicated, URI is invalid", key));
        } else {
            parameterMap.put(key, value);
        }
    }

    /**
     * The Nubits Address from the URI, if one was present. It's possible to have Nubits URI's with no address if a
     * r= payment protocol parameter is specified, though this form is not recommended as older wallets can't understand
     * it.
     */
    @Nullable
    public Address getAddress() {
        return (Address) parameterMap.get(FIELD_ADDRESS);
    }

    /**
     * @return The amount name encoded using a pure integer value based at
     *         10,000,000 units is 1 Nbt. May be null if no amount is specified
     */
    public Monetary getAmount() {
        return (Monetary) parameterMap.get(FIELD_AMOUNT);
    }

    /**
     * @return The label from the URI.
     */
    public String getLabel() {
        return (String) parameterMap.get(FIELD_LABEL);
    }

    /**
     * @return The message from the URI.
     */
    public String getMessage() {
        return (String) parameterMap.get(FIELD_MESSAGE);
    }

    /**
     * @return The URL where a payment request (as specified in BIP 70) may
     *         be fetched.
     */
    public String getPaymentRequestUrl() {
        return (String) parameterMap.get(FIELD_PAYMENT_REQUEST_URL);
    }

    /**
     * Returns the URLs where a payment request (as specified in BIP 70) may be fetched. The first URL is the main URL,
     * all subsequent URLs are fallbacks.
     */
    public List<String> getPaymentRequestUrls() {
        ArrayList<String> urls = new ArrayList<String>();
        while (true) {
            int i = urls.size();
            String paramName = FIELD_PAYMENT_REQUEST_URL + (i > 0 ? Integer.toString(i) : "");
            String url = (String) parameterMap.get(paramName);
            if (url == null)
                break;
            urls.add(url);
        }
        Collections.reverse(urls);
        return urls;
    }

    /**
     * @param name The name of the parameter
     * @return The parameter value, or null if not present
     */
    public Object getParameterByName(String name) {
        return parameterMap.get(name);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("NubitsURI[");
        boolean first = true;
        for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
            if (first) {
                first = false;
            } else {
                builder.append(",");
            }
            builder.append("'").append(entry.getKey()).append("'=").append("'").append(entry.getValue().toString()).append("'");
        }
        builder.append("]");
        return builder.toString();
    }

    public static String convertToNubitsURI(Address address, Coin amount, String label, String message) {
        return convertToNubitsURI(address.toString(), amount, label, message);
    }

    /**
     * Simple Nubits URI builder using known good fields.
     * 
     * @param address The Nubits address
     * @param amount The amount
     * @param label A label
     * @param message A message
     * @return A String containing the Nubits URI
     */
    public static String convertToNubitsURI(String address, @Nullable Coin amount, @Nullable String label,
            @Nullable String message) {
        checkNotNull(address);
        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException("Coin must be positive");
        }

        StringBuilder builder = new StringBuilder();
        builder.append(Nubits_SCHEME).append(":").append(address);

        boolean questionMarkHasBeenOutput = false;

        if (amount != null) {
            builder.append(QUESTION_MARK_SEPARATOR).append(FIELD_AMOUNT).append("=");
            builder.append(amount.toPlainString());
            questionMarkHasBeenOutput = true;
        }

        if (label != null && !"".equals(label)) {
            if (questionMarkHasBeenOutput) {
                builder.append(AMPERSAND_SEPARATOR);
            } else {
                builder.append(QUESTION_MARK_SEPARATOR);                
                questionMarkHasBeenOutput = true;
            }
            builder.append(FIELD_LABEL).append("=").append(encodeURLString(label));
        }

        if (message != null && !"".equals(message)) {
            if (questionMarkHasBeenOutput) {
                builder.append(AMPERSAND_SEPARATOR);
            } else {
                builder.append(QUESTION_MARK_SEPARATOR);
            }
            builder.append(FIELD_MESSAGE).append("=").append(encodeURLString(message));
        }

        return builder.toString();
    }

    /**
     * Encode a string using URL encoding
     * 
     * @param stringToEncode The string to URL encode
     */
    static String encodeURLString(String stringToEncode) {
        try {
            return java.net.URLEncoder.encode(stringToEncode, "UTF-8").replace("+", ENCODED_SPACE_CHARACTER);
        } catch (UnsupportedEncodingException e) {
            // should not happen - UTF-8 is a valid encoding
            throw new RuntimeException(e);
        }
    }
}