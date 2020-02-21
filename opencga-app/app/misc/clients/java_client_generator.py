#!/usr/bin/env python3

import argparse
import re
import sys
from rest_client_generator import RestClientGenerator


class JavaClientGenerator(RestClientGenerator):

    def __init__(self, server_url, output_dir):
        super().__init__(server_url, output_dir)

        self.java_types = set()
        self.type_imports = {
            'ObjectMap': 'org.opencb.commons.datastore.core.ObjectMap;'
        }
        self.ignore_types = [
            'Integer', 'String', 'boolean', 'int', 'Boolean'
        ]
        self.param_types = {
            'string': 'String',
            'integer': 'int',
            'int': 'int',
            'map': 'ObjectMap',
            'boolean': 'boolean',
            'enum': 'String'
        }

    def get_imports(self):
        headers = []
        headers.append('/*')
        headers.append('* Copyright 2015-2020 OpenCB')
        headers.append('*')
        headers.append('* Licensed under the Apache License, Version 2.0 (the "License");')
        headers.append('* you may not use this file except in compliance with the License.')
        headers.append('* You may obtain a copy of the License at')
        headers.append('*')
        headers.append('*     http://www.apache.org/licenses/LICENSE-2.0')
        headers.append('*')
        headers.append('* Unless required by applicable law or agreed to in writing, software')
        headers.append('* distributed under the License is distributed on an "AS IS" BASIS,')
        headers.append('* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.')
        headers.append('* See the License for the specific language governing permissions and')
        headers.append('* limitations under the License.')
        headers.append('*/')
        headers.append('')
        headers.append('package org.opencb.opencga.client.rest;')
        headers.append('')

        imports = set()
        imports.add('org.opencb.opencga.client.exceptions.ClientException;')
        imports.add('org.opencb.opencga.client.config.ClientConfiguration;')
        # imports.append('import org.opencb.opencga.client.rest.AbstractParentClient;')
        imports.add('org.opencb.opencga.core.response.RestResponse;')

        for java_type in self.java_types:
            if java_type in self.type_imports and java_type not in self.ignore_types:
                imports.add(self.type_imports[java_type])
            elif java_type not in self.ignore_types:
                raise Exception(java_type + ' not found')

        imports = remove_redundant_imports(list(imports))
        imports.sort()

        autogenerated_message = []
        autogenerated_message.append('/*')
        for text in self.get_autogenerated_message():
            if text == '':
                autogenerated_message.append('*')
            else:
                autogenerated_message.append('* ' + text)
        autogenerated_message.append('*/')

        return '\n'.join(headers) + '\n' + '\n'.join(['import ' + i for i in imports]) + '\n\n\n' + '\n'.join(autogenerated_message)

    def get_class_definition(self, category):
        self.java_types = set()

        text = []
        text.append('')
        text.append('')
        text.append('/**')
        text.append(' * This class contains methods for the {} webservices.'.format(
            self.categories[self.get_category_name(category)]))
        text.append(' *{}Client version: {}'.format(' ' * 4, self.version))
        text.append(' *{}PATH: {}'.format(' ' * 4, self.get_category_path(category)))
        text.append(' */')
        text.append('public class {}Client extends AbstractParentClient {{'.format(self.categories[self.get_category_name(category)]))
        text.append('')
        text.append('{}public {}Client(String token, ClientConfiguration configuration) {{'.format(' ' * 4, self.categories[self.get_category_name(category)]))
        text.append('{}super(token, configuration);'.format(' ' * 8))
        text.append('{}}}'.format(' ' * 4))
        return '\n'.join(text)

    def get_class_end(self):
        return '}\n'

    def get_method_definition(self, category, endpoint):
        parameters = self.get_method_parameters(endpoint)
        typed_parameters = []
        print("Processing " + self.get_endpoint_path(endpoint))
        for parameter in parameters:
            typed_parameters.append(self.get_parameter_type(parameter) + ' ' + parameter)

        response_type = self.get_valid_parameter_type(self.get_endpoint_response(endpoint), self.get_endpoint_response_class(endpoint))

        text = []
        append_comment_text(text, '', 4)
        append_comment_text(text, '{}/**'.format(' ' * 4), 4)
        append_comment_text(text, '{}* {}'.format(' ' * 5, self.get_endpoint_description(endpoint)), 5)
        for parameter in parameters:
            append_comment_text(text, '{}* @param {} {}'.format(' ' * 5, parameter, self.get_parameter_description(parameter)), 5)

            if parameter == 'params':
                for parameter in self.get_optional_parameters(endpoint):
                    append_comment_text(text, '{}* {} {}: {}'.format(' ' * 5, ' ' * 5, parameter,
                                                                        self.get_parameter_description(parameter)), 5, 12)

        append_comment_text(text, '{}* @return a RestResponse object.'.format(' ' * 5), 5)
        append_comment_text(text, '{}* @throws ClientException ClientException if there is any server error.'.format(' ' * 5), 5)
        append_comment_text(text, '{}*/'.format(' ' * 5), 5)

        append_text(text, '{}public RestResponse<{}> {}({}) throws ClientException {{'.format(
            ' ' * 4, response_type, self.get_method_name(endpoint, category),
            ', '.join(typed_parameters)), 4)

        if 'params' in parameters:
            append_text(text, '{}params = params != null ? params : new ObjectMap();'.format(' ' * 8), 8)
        else:
            append_text(text, '{}ObjectMap params = new ObjectMap();'.format(' ' * 8), 8)

        for parameter in parameters:
            if parameter not in ['params', 'data'] and not self.is_path_param(parameter):
                append_text(text, '{}params.putIfNotNull("{}", {});'.format(' ' * 8, parameter, parameter), 8)

        if 'data' in parameters:
            append_text(text, '{}params.put("body", data);'.format(' ' * 8), 8)

        append_text(text, '{}return execute("{}", {}, {}, {}, {}, params, {}, {}.class);'.format((' ' * 8),
            self.get_category_path(category),
            self.get_endpoint_id1() if self.get_endpoint_id1() else 'null',
            '"' + self.get_endpoint_subcategory() + '"' if self.get_endpoint_subcategory() else 'null',
            self.get_endpoint_id2() if self.get_endpoint_id2() else 'null',
            '"' + self.get_endpoint_action() + '"' if self.get_endpoint_action() else 'null',
            self.get_endpoint_method(endpoint),
            response_type), 8)

        text.append('{}}}'.format(' ' * 4))
        return '\n'.join(text)

    def get_file_name(self, category):
        return self.categories[self.get_category_name(category)] + "Client.java"

    def get_method_name(self, endpoint, category):
        method_name = super().get_method_name(endpoint, category)
        # Convert to cammel case
        method_name = method_name.replace('_', ' ').title().replace(' ', '')
        return method_name[0].lower() + method_name[1:]

    def get_method_parameters(self, endpoint):
        parameters = []
        parameters.extend(self.get_path_params(endpoint))
        parameters.extend(self.get_mandatory_query_params(endpoint))

        if 'data' in self.parameters:
            parameters.append('data')

        if self.has_optional_params(endpoint):
            parameters.append('params')

        return parameters

    def get_parameter_description(self, parameter):
        if parameter != 'params':
            return super().get_parameter_description(parameter)
        else:
            return 'Map containing any of the following optional parameters.'

    def get_parameter_type(self, parameter):
        my_type = ''
        my_import = ''
        if parameter != 'params':
            param_type = super().get_parameter_type(parameter)
            if param_type in self.param_types:
                my_type = self.param_types[param_type]
                my_import = self.get_parameter_type_class(parameter)
            elif param_type == 'object':
                my_import = self.get_parameter_type_class(parameter)
                my_type = my_import.split('.')[-1]
            else:
                raise Exception('Param ' + param_type + ' not found')
        return self.get_valid_parameter_type(my_type, my_import)

    def get_valid_parameter_type(self, my_type, my_import):
        if my_type.endswith(';'):
            my_type = my_type[:-1]

        if '$' in my_type:
            my_type = my_type.replace('$', '.')
        if '$' in my_import:
            my_import = my_import.replace('$', '.')

        if my_type == '' or my_type == 'Map':
            my_type = 'ObjectMap'
        else:
            self.type_imports[my_type] = my_import
        self.java_types.add(my_type)

        return my_type


def remove_redundant_imports(imports):
    to_remove = []
    for i in range(len(imports) - 1):
        for j in range(i + 1, len(imports)):
            if imports[i][:-1] + "." in imports[j][:-1]:
                to_remove.append(imports[j])
            elif imports[j][:-1] + "." in imports[i][:-1]:
                to_remove.append(imports[i])

    for my_import in to_remove:
        imports.remove(my_import)

    return imports


def append_text(array, string, sep):
    _append_text(array, string, sep, sep, False)


def append_comment_text(array, string, sep, sep2=None):
    _append_text(array, string, sep, sep if sep2 is None else sep2, True)


def _append_text(array, string, sep, sep2, comment):
    if len(string) <= 140:
        array.append(string)
    else:
        my_string = string
        throw_string = None

        if 'throws' in my_string:
            pos = my_string.find('throws')
            throw_string = my_string[pos:]
            my_string = my_string[:pos - 1]

        while len(my_string) > 140:
            max = 0
            for it in re.finditer(' ', my_string):
                pos = it.start()
                if len(string[:pos]) < 140:
                    max = pos
            array.append(my_string[:max])
            text = ' ' * sep
            if comment:
                text += '*'
            text += ' ' * sep2 + my_string[max + 1:]
            my_string = text

        if throw_string:
            if len(my_string + ' ' + throw_string) <= 140:
                array.append(my_string + ' ' + throw_string)
            else:
                array.append(my_string)
                array.append(' ' * sep * 3 + throw_string)
        else:
            array.append(my_string)


def _setup_argparse():
    desc = 'This script creates automatically all RestClients files'
    parser = argparse.ArgumentParser(description=desc)

    parser.add_argument('server_url', help='server URL')
    parser.add_argument('output_dir', help='output directory')
    args = parser.parse_args()
    return args


def main():
    # Getting arg parameters
    args = _setup_argparse()

    client_generator = JavaClientGenerator(args.server_url, args.output_dir)
    client_generator.create_rest_clients()


if __name__ == '__main__':
    sys.exit(main())
