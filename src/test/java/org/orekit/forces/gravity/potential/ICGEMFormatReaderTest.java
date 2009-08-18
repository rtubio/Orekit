/* Copyright 2002-2008 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.forces.gravity.potential;


import java.io.IOException;
import java.text.ParseException;

import org.junit.Assert;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.errors.OrekitException;

public class ICGEMFormatReaderTest {

    @Test
    public void testRegular05c() throws IOException, ParseException, OrekitException {
        Utils.setDataRoot("potential");
        PotentialReaderFactory factory = new PotentialReaderFactory("g007_eigen_05c_coef", null, null);
        PotentialCoefficientsProvider provider = factory.getPotentialProvider();
        double[][] C = provider.getC(5, 5, true);;
        double[][] S = provider.getS(5, 5, true);

        Assert.assertEquals(0.957212879862E-06,C[3][0],  0);
        Assert.assertEquals(00.174804558032E-06,C[5][5],  0);
        Assert.assertEquals(0, S[4][0],  0);
        Assert.assertEquals(0.308816581016E-06,S[4][4],  0);
        Assert.assertEquals(0.3986004415E+15,provider.getMu(),  0);
        Assert.assertEquals(0.6378136460E+07,provider.getAe(),  0);

    }

    @Test(expected=OrekitException.class)
    public void testCorruptedFile1() throws IOException, ParseException, OrekitException {
        Utils.setDataRoot("potential");
        PotentialReaderFactory factory = new PotentialReaderFactory("g007_eigen_corrupted1_coef", null, null);
        factory.getPotentialProvider();
    }

    @Test(expected=OrekitException.class)
    public void testCorruptedFile2() throws IOException, ParseException, OrekitException {
        Utils.setDataRoot("potential");
        PotentialReaderFactory factory = new PotentialReaderFactory("g007_eigen_corrupted2_coef", null, null);
        factory.getPotentialProvider();
    }

}
