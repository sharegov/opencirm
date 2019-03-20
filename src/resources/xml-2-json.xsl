<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:ebl="urn:ebay:apis:eBLBaseComponents" exclude-result-prefixes="ebl">
	<!--====================================================================================
	Original version by : Holten Norris ( holtennorris at yahoo.com )
	Current version maintained  by: Alan Lewis (alanlewis at gmail.com)
	Thanks to Venu Reddy from eBay XSLT team for help with the array detection code
	Protected by CDDL open source license.  
	Transforms XML into JavaScript objects, using a JSON format.
	===================================================================================== -->
	<xsl:output method="text" encoding="UTF-8"/>
	<xsl:strip-space elements="*"/>
	<xsl:template match="*">
		<xsl:param name="recursionCnt">0</xsl:param>
		<xsl:param name="isLast">1</xsl:param>
		<xsl:param name="inArray">0</xsl:param>
		<xsl:if test="$recursionCnt=0">
			<xsl:text>{</xsl:text>
		</xsl:if>
		<!-- test what type of data to output  -->
		<xsl:variable name="elementDataType">
			<xsl:value-of select="number(text())"/>
		</xsl:variable>
		<xsl:variable name="elementData">
			<!-- TEXT ( use quotes ) -->
			<xsl:if test="string($elementDataType) ='NaN'">
				<xsl:if test="boolean(text())">
				    <xsl:call-template name="escape-string">
    		            <xsl:with-param name="s" select="."/>
    			    </xsl:call-template>
				</xsl:if>
			</xsl:if>
			<!-- NUMBER (no quotes ) -->
			<xsl:if test="string($elementDataType) !='NaN'">
				<xsl:text/><xsl:value-of select="format-number(text(), '#')"/><xsl:text/>
			</xsl:if>
			<!-- NULL -->
			<xsl:if test="not(*)">
				<xsl:if test="not(text())">
					<xsl:text/>null<xsl:text/>
				</xsl:if>
			</xsl:if>
		</xsl:variable>
		<xsl:variable name="hasRepeatElements">
			<xsl:for-each select="*">
				<xsl:if test="name() = name(preceding-sibling::*) or name() = name(following-sibling::*)">
					<xsl:text/>true<xsl:text/>
				</xsl:if>
			</xsl:for-each>
		</xsl:variable>
		<xsl:if test="not(count(@*) &gt; 0)">
		 <xsl:text/>"<xsl:value-of select="local-name()"/>":<xsl:value-of select="$elementData"/><xsl:text/>
		</xsl:if>
		<xsl:if test="count(@*) &gt; 0">
		<xsl:text/>"<xsl:value-of select="local-name()"/>":{"content":<xsl:value-of select="$elementData"/><xsl:text/>
			<xsl:for-each select="@*">
				<xsl:if test="position()=1">,</xsl:if>
				<!-- test what type of data to output  -->
				<xsl:variable name="dataType">
					<xsl:text/><xsl:value-of select="number(.)"/><xsl:text/>
				</xsl:variable>
				<xsl:variable name="data">
					<!-- TEXT ( use quotes ) -->
					<xsl:if test="string($dataType) ='NaN'">
				        <xsl:call-template name="escape-string">
    		                <xsl:with-param name="s" select="."/>
    			        </xsl:call-template>
    			    </xsl:if>
					<!-- NUMBER (no quotes ) -->
					<xsl:if test="string($dataType) !='NaN'">
						<xsl:text/><xsl:value-of select="format-number(current(), '#')"/><xsl:text/>
					</xsl:if>
				</xsl:variable>
				<xsl:text/><xsl:value-of select="local-name()"/>:<xsl:value-of select="$data"/><xsl:text/>
				<xsl:if test="position() !=last()">,</xsl:if>
			</xsl:for-each>
		<xsl:text/>}<xsl:text/>
		</xsl:if>
		<xsl:if test="not($hasRepeatElements = '')">
					<xsl:text/>[{<xsl:text/>
				</xsl:if>
		<xsl:for-each select="*">
			<xsl:if test="position()=1">
				<xsl:if test="$hasRepeatElements = ''">
					<xsl:text>{</xsl:text>
				</xsl:if>
			</xsl:if>
			<xsl:apply-templates select="current()">
				<xsl:with-param name="recursionCnt" select="$recursionCnt+1"/>
				<xsl:with-param name="isLast" select="position()=last()"/>
				<xsl:with-param name="inArray" select="not($hasRepeatElements = '')"/>
			</xsl:apply-templates>
			<xsl:if test="position()=last()">
				<xsl:if test="$hasRepeatElements = ''">
					<xsl:text>}</xsl:text>
				</xsl:if>
			</xsl:if>
		</xsl:for-each>
		<xsl:if test="not($hasRepeatElements = '')">
					<xsl:text/>}]<xsl:text/>
				</xsl:if>
		<xsl:if test="not( $isLast )">
			<xsl:if test="$inArray = 'true'">
				<xsl:text>}</xsl:text>
			</xsl:if>
			<xsl:text/>,<xsl:text/>
			<xsl:if test="$inArray = 'true'">
				<xsl:text>{</xsl:text>
			</xsl:if>
		</xsl:if>
		<xsl:if test="$recursionCnt=0">}</xsl:if>
	</xsl:template>

	<!-- hilpold escape extension -->
	<!-- Main template for escaping strings; used by above template and for object-properties
     Responsibilities: placed quotes around string, and chain up to next filter, escape-bs-string -->
<xsl:template name="escape-string">
    <xsl:param name="s"/>
    <xsl:text>"</xsl:text>
    <xsl:call-template name="escape-bs-string">
        <xsl:with-param name="s" select="$s"/>
    </xsl:call-template>
    <xsl:text>"</xsl:text>
</xsl:template>

<!-- Escape the backslash (\) before everything else. -->
<xsl:template name="escape-bs-string">
    <xsl:param name="s"/>
    <xsl:choose>
        <xsl:when test="contains($s,'\')">
            <xsl:call-template name="escape-quot-string">
                <xsl:with-param name="s" select="concat(substring-before($s,'\'),'\\')"/>
            </xsl:call-template>
            <xsl:call-template name="escape-bs-string">
                <xsl:with-param name="s" select="substring-after($s,'\')"/>
            </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
            <xsl:call-template name="escape-quot-string">
                <xsl:with-param name="s" select="$s"/>
            </xsl:call-template>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<!-- Escape the double quote ("). -->
<xsl:template name="escape-quot-string">
    <xsl:param name="s"/>
    <xsl:choose>
        <xsl:when test="contains($s,'&quot;')">
            <xsl:call-template name="encode-string">
                <xsl:with-param name="s" select="concat(substring-before($s,'&quot;'),'\&quot;')"/>
            </xsl:call-template>
            <xsl:call-template name="escape-quot-string">
                <xsl:with-param name="s" select="substring-after($s,'&quot;')"/>
            </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
            <xsl:call-template name="encode-string">
                <xsl:with-param name="s" select="$s"/>
            </xsl:call-template>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<!-- Replace tab, line feed and/or carriage return by its matching escape code. Can't escape backslash
     or double quote here, because they don't replace characters (&#x0; becomes \t), but they prefix
     characters (\ becomes \\). Besides, backslash should be seperate anyway, because it should be
     processed first. This function can't do that. -->
<xsl:template name="encode-string">
    <xsl:param name="s"/>
    <xsl:choose>
        <!-- tab -->
        <xsl:when test="contains($s,'&#x9;')">
            <xsl:call-template name="encode-string">
                <xsl:with-param name="s" select="concat(substring-before($s,'&#x9;'),'\t',substring-after($s,'&#x9;'))"/>
            </xsl:call-template>
        </xsl:when>
        <!-- line feed -->
        <xsl:when test="contains($s,'&#xA;')">
            <xsl:call-template name="encode-string">
                <xsl:with-param name="s" select="concat(substring-before($s,'&#xA;'),'\n',substring-after($s,'&#xA;'))"/>
            </xsl:call-template>
        </xsl:when>
        <!-- carriage return -->
        <xsl:when test="contains($s,'&#xD;')">
            <xsl:call-template name="encode-string">
                <xsl:with-param name="s" select="concat(substring-before($s,'&#xD;'),'\r',substring-after($s,'&#xD;'))"/>
            </xsl:call-template>
        </xsl:when>
        <xsl:otherwise><xsl:value-of select="$s"/></xsl:otherwise>
    </xsl:choose>
</xsl:template>
</xsl:stylesheet>
