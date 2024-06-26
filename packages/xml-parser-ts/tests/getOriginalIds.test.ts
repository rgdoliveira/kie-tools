/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { XmlParserTsIdRandomizer } from "../src/idRandomizer";
import { v4 as uuid } from "uuid";

export const elements = {
  root: "root",
  nested: "nested",
  person: "person",
  address: "address",
  education: "education",
};

export const meta = {
  root: {
    nested: { type: "nested", isArray: false, fromType: "root", xsdType: "// local type" },
  },
  nested: {
    id: { type: "string", isArray: false, fromType: "root", xsdType: "xsd:ID" },
    person: { type: "person", isArray: false, fromType: "nested", xsdType: "// local type" },
    people: { type: "person", isArray: true, fromType: "nested", xsdType: "// local type" },
  },
  address: {
    id: { type: "string", isArray: false, fromType: "root", xsdType: "xsd:ID" },
    street: { type: "string", isArray: false, fromType: "address", xsdType: "xsd:string" },
    country: { type: "string", isArray: false, fromType: "address", xsdType: "xsd:string" },
    number: { type: "integer", isArray: false, fromType: "address", xsdType: "xsd:int" },
  },
  education: {
    id: { type: "string", isArray: false, fromType: "root", xsdType: "xsd:ID" },
    school: { type: "string", isArray: false, fromType: "education", xsdType: "xsd:string" },
  },
  person: {
    id: { type: "string", isArray: false, fromType: "root", xsdType: "xsd:ID" },
    address: { type: "address", isArray: false, fromType: "address", xsdType: "// local type" },
    education: { type: "education", isArray: true, fromType: "education", xsdType: "// local type" },
    luckyIds: { type: "string", isArray: true, fromType: "person", xsdType: "xsd:ID" },
  },
} as const;

/**
 * Generates a UUID with a format similar to _6EFDBCB4-F4AF-4E9A-9A66-2A9F24185674
 */
const generateUuid = () => {
  return `_${uuid()}`.toLocaleUpperCase();
};

function getXmlParserTsIdRandomizer() {
  return new XmlParserTsIdRandomizer({
    meta: meta,
    elements: elements,
    newIdGenerator: generateUuid,
    matchers: [],
  });
}

describe("getOriginalIds", () => {
  test("undefined", () => {
    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: undefined,
        type: "person",
        attr: "address",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set());
  });

  test("object", () => {
    const ids = Array.from({ length: 1 }, () => generateUuid());
    const [addressId] = ids;

    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: { id: addressId, street: "test", number: 1, country: "Brazil" },
        type: "person",
        attr: "address",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set(ids));
  });

  test("object with undefined id", () => {
    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: { id: undefined },
        type: "person",
        attr: "address",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set());
  });

  test("array of objects", () => {
    const ids = Array.from({ length: 1 }, () => generateUuid());
    const [educationId] = ids;

    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: [{ id: educationId, school: "MIT" }],
        type: "person",
        attr: "education",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set(ids));
  });

  test("array of ids", () => {
    const ids = Array.from({ length: 3 }, () => generateUuid());
    const [lucky1, lucky2, lucky3] = ids;

    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: [lucky1, lucky2, lucky3],
        type: "person",
        attr: "luckyIds",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set(ids));
  });

  test("array of undefined", () => {
    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: [undefined, undefined],
        type: "person",
        attr: "luckyIds",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set());
  });

  test("complete example - nested objects", () => {
    const ids = Array.from({ length: 7 }, () => generateUuid());
    const [rootId, personId, addressId, educationId, lucky1, lucky2, lucky3] = ids;

    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: {
          id: rootId,
          person: {
            id: personId,
            address: { id: addressId, street: "test", number: 1, country: "Brazil" },
            education: [{ id: educationId, school: "MIT" }],
            luckyIds: [lucky1, lucky2, lucky3],
          },
        },
        type: "root",
        attr: "nested",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set(ids));
  });

  test("complete example - nested arrays", () => {
    const ids = Array.from({ length: 13 }, () => generateUuid());
    const [
      nestedId,
      firstPersonId,
      firstAddressId,
      firstEducationId,
      firstLucky1,
      firstLucky2,
      secondPersonId,
      secondAddressId,
      secondEducation1Id,
      secondEducation2Id,
      secondLucky1,
      secondLucky2,
      secondLucky3,
    ] = ids;

    const originalIds = getXmlParserTsIdRandomizer()
      .ack({
        json: {
          id: nestedId,
          people: [
            {
              id: firstPersonId,
              address: { id: firstAddressId, street: "foo", number: 1, country: "Brazil" },
              education: [{ id: firstEducationId, school: "MIT" }],
              luckyIds: [firstLucky1, firstLucky2],
            },
            {
              id: secondPersonId,
              address: { id: secondAddressId, street: "bar", number: 2, country: "US" },
              education: [
                { id: secondEducation1Id, school: "MIT" },
                { id: secondEducation2Id, school: "Harvard" },
              ],
              luckyIds: [secondLucky1, secondLucky2, secondLucky3],
            },
          ],
        },
        type: "root",
        attr: "nested",
      })
      .getOriginalIds();

    expect(originalIds).toEqual(new Set(ids));
  });
});
